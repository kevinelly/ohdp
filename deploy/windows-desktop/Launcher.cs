using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Collections.Generic;
using System.Web.Script.Serialization;

internal static class Program
{
    internal static readonly string AppHome = AppDomain.CurrentDomain.BaseDirectory;
    internal static readonly string DataHome = Path.GetFullPath(
        Environment.GetEnvironmentVariable("BHFYZX_DATA_DIR") ??
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BHFYZX", "Data"));
    internal static string InstanceName;
    internal static bool Quiet;
    internal static bool NoBrowser;
    internal static int ExitCode;

    [STAThread]
    private static int Main(string[] args)
    {
        Quiet = Array.IndexOf(args, "--quiet") >= 0;
        NoBrowser = Quiet || Array.IndexOf(args, "--no-browser") >= 0;
        using (var hash = SHA256.Create())
            InstanceName = "Local\\BHFYZX-" + BitConverter.ToString(hash.ComputeHash(
                Encoding.UTF8.GetBytes(DataHome.ToUpperInvariant()))).Replace("-", "").Substring(0, 24);
        try
        {
            if (Array.IndexOf(args, "--stop") >= 0) return StopExisting();
            Directory.CreateDirectory(DataHome);
            using (var mutex = new Mutex(false, InstanceName))
            {
                bool acquired;
                try { acquired = mutex.WaitOne(0); }
                catch (AbandonedMutexException) { acquired = true; }
                if (!acquired)
                {
                    using (var reopen = EventWaitHandle.OpenExisting(InstanceName + "-open")) reopen.Set();
                    return 0;
                }
                try
                {
                    Application.EnableVisualStyles();
                    Application.SetCompatibleTextRenderingDefault(false);
                    using (var form = new LauncherForm()) Application.Run(form);
                }
                finally { mutex.ReleaseMutex(); }
            }
            return ExitCode;
        }
        catch (Exception ex)
        {
            Directory.CreateDirectory(DataHome);
            File.AppendAllText(Path.Combine(DataHome, "launcher-error.log"), DateTime.Now + " " + ex + Environment.NewLine);
            if (!Quiet) MessageBox.Show("启动失败：" + ex.Message + "\n\n数据目录：" + DataHome, "北海职检平台");
            return 1;
        }
    }

    private static int StopExisting()
    {
        try { using (var stop = EventWaitHandle.OpenExisting(InstanceName + "-stop")) stop.Set(); }
        catch (WaitHandleCannotBeOpenedException) { return 0; }
        using (var mutex = new Mutex(false, InstanceName))
        {
            bool stopped;
            try { stopped = mutex.WaitOne(TimeSpan.FromSeconds(90)); }
            catch (AbandonedMutexException) { stopped = true; }
            if (stopped) mutex.ReleaseMutex();
            return stopped ? 0 : 1;
        }
    }

    internal static void Open(string path) { Process.Start(new ProcessStartInfo(path) { UseShellExecute = true }); }
    internal static string Secret()
    {
        byte[] bytes = new byte[18];
        using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
        return Convert.ToBase64String(bytes).Replace("+", "A").Replace("/", "b").TrimEnd('=');
    }
}

internal sealed class LauncherForm : Form
{
    private readonly Label status = new Label();
    private readonly Label account = new Label();
    private readonly Button openButton = new Button();
    private readonly Button backupButton = new Button();
    private readonly Button stopButton = new Button();
    private readonly Button importButton = new Button();
    private readonly Button deleteButton = new Button();
    private readonly Label sampleStatus = new Label();
    private bool samplesImported;
    private bool samplesAvailable;
    private bool sampleBusy;
    private readonly NotifyIcon tray;
    private readonly EventWaitHandle stopEvent;
    private readonly EventWaitHandle openEvent;
    private RegisteredWaitHandle stopWait;
    private RegisteredWaitHandle openWait;
    private Process server;
    private string token;
    private string url;
    private bool ready;
    private bool stopping;
    private bool canClose;
    private readonly object logLock = new object();

    internal LauncherForm()
    {
        Text = "北海职业卫生数据处理平台";
        ClientSize = new Size(590, 450);
        Font = new Font("Microsoft YaHei UI", 10F);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        var heading = new Label { Text = "北海职业卫生数据处理平台", Font = new Font(Font.FontFamily, 17F, FontStyle.Bold), Location = new Point(24, 24), Size = new Size(545, 38) };
        status.SetBounds(26, 77, 540, 50);
        status.Text = "正在启动，请稍候……首次启动需要初始化数据库。";
        account.SetBounds(26, 132, 540, 50);
        var data = new LinkLabel { Text = "打开数据文件夹", Location = new Point(26, 195), AutoSize = true };
        data.LinkClicked += delegate { Program.Open(Program.DataHome); };
        var credentials = new LinkLabel { Text = "查看首次登录信息", Location = new Point(210, 195), AutoSize = true };
        credentials.LinkClicked += delegate { Program.Open(Path.Combine(Program.DataHome, "首次登录信息.txt")); };
        var detailedManual = new LinkLabel { Text = "下载详尽手册（Word）", Location = new Point(26, 225), AutoSize = true };
        detailedManual.LinkClicked += delegate { SaveManual("detailed-guide.docx", "详尽操作手册"); };
        var quickManual = new LinkLabel { Text = "下载简明指引（Word）", Location = new Point(306, 225), AutoSize = true };
        quickManual.LinkClicked += delegate { SaveManual("quick-guide.docx", "简明操作指引"); };
        openButton.Text = "打开系统";
        openButton.SetBounds(26, 255, 160, 42);
        openButton.Enabled = false;
        openButton.Click += delegate { OpenBrowser(); };
        backupButton.Text = "备份数据";
        backupButton.SetBounds(206, 255, 160, 42);
        backupButton.Enabled = false;
        backupButton.Click += async delegate { await Backup(); };
        stopButton.Text = "停止并退出";
        stopButton.SetBounds(386, 255, 170, 42);
        stopButton.Enabled = false;
        stopButton.Click += async delegate { await ExitApplication(); };
        sampleStatus.SetBounds(26, 318, 540, 46);
        sampleStatus.Text = "测试数据：正在检查……";
        importButton.Text = "导入测试数据";
        importButton.SetBounds(26, 380, 250, 42);
        importButton.Enabled = false;
        importButton.Click += async delegate { await ChangeSamples(true); };
        deleteButton.Text = "删除测试数据";
        deleteButton.SetBounds(306, 380, 250, 42);
        deleteButton.Enabled = false;
        deleteButton.Click += async delegate { await ChangeSamples(false); };
        Controls.AddRange(new Control[] { heading, status, account, data, credentials, detailedManual, quickManual, openButton, backupButton, stopButton, sampleStatus, importButton, deleteButton });
        var menu = new ContextMenuStrip();
        menu.Items.Add("打开系统", null, delegate { Reveal(); if (ready) OpenBrowser(); });
        menu.Items.Add("查看运行状态", null, delegate { Reveal(); });
        menu.Items.Add("停止并退出", null, async delegate { await ExitApplication(); });
        tray = new NotifyIcon { Icon = Icon, Text = "北海职业卫生数据处理平台", ContextMenuStrip = menu, Visible = !Program.Quiet };
        tray.DoubleClick += delegate { Reveal(); if (ready) OpenBrowser(); };
        stopEvent = new EventWaitHandle(false, EventResetMode.AutoReset, Program.InstanceName + "-stop");
        openEvent = new EventWaitHandle(false, EventResetMode.AutoReset, Program.InstanceName + "-open");
        Shown += async delegate
        {
            if (Program.Quiet) Hide();
            stopWait = ThreadPool.RegisterWaitForSingleObject(stopEvent, delegate { Post(async delegate { await ExitApplication(); }); }, null, -1, false);
            openWait = ThreadPool.RegisterWaitForSingleObject(openEvent, delegate { Post(delegate { Reveal(); if (ready && !Program.NoBrowser) OpenBrowser(); }); }, null, -1, false);
            await StartServer();
        };
        FormClosing += delegate(object sender, FormClosingEventArgs e)
        {
            if (!canClose && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                tray.ShowBalloonTip(2500, "系统仍在运行", "可通过托盘图标打开系统，或选择“停止并退出”。", ToolTipIcon.Info);
            }
        };
    }

    private void Post(Action action) { if (!IsDisposed && IsHandleCreated) BeginInvoke(action); }
    private void Reveal() { if (Program.Quiet) return; Show(); WindowState = FormWindowState.Normal; Activate(); }
    private void OpenBrowser()
    {
        try { Program.Open(url); }
        catch (Exception ex) { MessageBox.Show(this, "请在浏览器中打开：" + url + "\n" + ex.Message, Text); }
    }

    private async Task StartServer()
    {
        ready = false;
        status.Text = "正在启动，请稍候……";
        SetControls(false);
        try
        {
            Directory.CreateDirectory(Path.Combine(Program.DataHome, "database"));
            Directory.CreateDirectory(Path.Combine(Program.DataHome, "logs"));
            string secretsPath = Path.Combine(Program.DataHome, "installation.keys");
            if (!File.Exists(secretsPath))
            {
                // Do not create new encryption credentials beside a pre-existing database.
                if (File.Exists(Path.Combine(Program.DataHome, "database", "ohdp.mv.db")))
                    throw new IOException("数据库已存在，但缺少 installation.keys。请恢复完整备份。");
                File.WriteAllLines(secretsPath, new[] { Program.Secret(), "Test@345" }, Encoding.UTF8);
            }
            var secrets = File.ReadAllLines(secretsPath, Encoding.UTF8);
            if (secrets.Length != 2) throw new IOException("installation.keys 文件不完整，请恢复备份。");
            string credentials = Path.Combine(Program.DataHome, "首次登录信息.txt");
            if (secrets[1] != "Test@345") { secrets[1] = "Test@345"; File.WriteAllLines(secretsPath, secrets, Encoding.UTF8); }
            File.WriteAllText(credentials, "北海职业卫生数据处理平台\r\n\r\n管理员账号：admin\r\n默认初始密码：Test@345\r\n\r\n首次使用新版会设置此密码。之后如自行修改，请使用修改后的密码；重启不会重置密码。\r\n数据保存在：" + Program.DataHome, Encoding.UTF8);
            account.Text = "管理员账号：admin\n默认密码：Test@345（如已修改，请使用修改后的密码）";
            int preferredPort = 18788;
            string portPath = Path.Combine(Program.DataHome, "port.txt");
            int savedPort;
            if (File.Exists(portPath) && int.TryParse(File.ReadAllText(portPath), out savedPort) && savedPort >= 1024 && savedPort <= 65535) preferredPort = savedPort;
            int port = await Task.Run(() => FindPort(preferredPort));
            url = "http://127.0.0.1:" + port;
            File.WriteAllText(portPath, port.ToString());
            token = Program.Secret();
            var start = new ProcessStartInfo(Path.Combine(Program.AppHome, "runtime", "bin", "java.exe"));
            start.Arguments = "-Xms128m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar \"" + Path.Combine(Program.AppHome, "app.jar") + "\" --spring.profiles.active=desktop --spring.config.additional-location=\"" + new Uri(Path.Combine(Program.AppHome, "application-desktop.properties")).AbsoluteUri + "\"";
            start.WorkingDirectory = Program.DataHome;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = start.RedirectStandardError = true;
            start.StandardOutputEncoding = start.StandardErrorEncoding = Encoding.UTF8;
            start.EnvironmentVariables["BHFYZX_DATA_HOME"] = Program.DataHome.Replace('\\', '/');
            start.EnvironmentVariables["BHFYZX_WEB_URI"] = new Uri(Path.Combine(Program.AppHome, "web") + Path.DirectorySeparatorChar).AbsoluteUri;
            start.EnvironmentVariables["BHFYZX_PORT"] = port.ToString();
            start.EnvironmentVariables["BHFYZX_DATABASE_PASSWORD"] = secrets[0];
            start.EnvironmentVariables["BHFYZX_INITIAL_PASSWORD"] = secrets[1];
            start.EnvironmentVariables["BHFYZX_INSTANCE_TOKEN"] = token;
            start.EnvironmentVariables["BHFYZX_SAMPLE_ZIP"] = Path.Combine(Program.AppHome, "online-sample.zip");
            // The desktop profile owns its configuration even on developer machines.
            start.EnvironmentVariables.Remove("SPRING_APPLICATION_JSON");
            start.EnvironmentVariables.Remove("JAVA_TOOL_OPTIONS");
            start.EnvironmentVariables.Remove("JDK_JAVA_OPTIONS");
            start.EnvironmentVariables.Remove("_JAVA_OPTIONS");
            string consoleLog = Path.Combine(Program.DataHome, "logs", "launcher-console.log");
            if (File.Exists(consoleLog) && new FileInfo(consoleLog).Length > 10 * 1024 * 1024) File.WriteAllText(consoleLog, "");
            server = new Process { StartInfo = start, EnableRaisingEvents = true };
            DataReceivedEventHandler log = delegate(object sender, DataReceivedEventArgs e)
            {
                if (e.Data == null) return;
                try { lock (logLock) File.AppendAllText(consoleLog, e.Data + Environment.NewLine, Encoding.UTF8); } catch { }
            };
            server.OutputDataReceived += log;
            server.ErrorDataReceived += log;
            server.Exited += delegate { Post(delegate { if (!stopping && ready) { ready = false; SetControls(false); stopButton.Enabled = true; status.Text = "服务已停止。请退出后重新启动；详细原因见数据目录中的 logs。"; } }); };
            server.Start();
            server.BeginOutputReadLine();
            server.BeginErrorReadLine();
            bool healthy = await Task.Run(async delegate
            {
                for (int attempt = 0; attempt < 180; attempt++)
                {
                    if (server.HasExited || stopping) return false;
                    try { if (Request("/desktop/status", false).Contains("\"ready\"")) return true; } catch { }
                    await Task.Delay(1000);
                }
                return false;
            });
            if (stopping) return;
            if (!healthy) throw new IOException("服务未能启动，请查看数据目录中的 logs 文件夹。");
            ready = true;
            status.Text = "系统已启动：" + url + "\n关闭此窗口后仍可在浏览器中使用。";
            try { await ApplyInstallOptions(); await RefreshSamples(); }
            catch (Exception ex) { status.Text = "系统已启动，测试数据操作失败：" + ex.Message; samplesAvailable = File.Exists(Path.Combine(Program.AppHome, "online-sample.zip")); }
            SetControls(true);
            if (!Program.NoBrowser) OpenBrowser();
        }
        catch (Exception ex)
        {
            Program.ExitCode = 1;
            status.Text = "启动失败：" + ex.Message;
            stopButton.Enabled = true;
            File.AppendAllText(Path.Combine(Program.DataHome, "launcher-error.log"), DateTime.Now + " " + ex + Environment.NewLine);
            if (server != null && !server.HasExited) { server.Kill(); server.WaitForExit(10000); }
            if (Program.Quiet) { canClose = true; Close(); }
        }
    }

    private static int FindPort(int preferred)
    {
        for (int i = 0; i < 100; i++)
        {
            int candidate = i == 0 ? preferred : 18788 + i;
            var listener = new TcpListener(IPAddress.Loopback, candidate);
            try { listener.Start(); return candidate; }
            catch (SocketException) { }
            finally { listener.Stop(); }
        }
        throw new IOException("没有可用的本地端口，请关闭其他程序后重试。");
    }

    private string Request(string path, bool shutdown)
    {
        var request = (HttpWebRequest)WebRequest.Create(url + path);
        request.Proxy = null;
        request.Timeout = path.StartsWith("/desktop/sample-data") ? 120000 : 1500;
        request.ReadWriteTimeout = request.Timeout;
        request.Headers["X-Desktop-Token"] = token;
        if (shutdown)
        {
            request.Method = "POST";
            request.ContentLength = 0;
            request.Headers["X-Desktop-Token"] = token;
        }
        try {
            using (var response = request.GetResponse())
            using (var reader = new StreamReader(response.GetResponseStream())) return reader.ReadToEnd();
        } catch (WebException ex) {
            if (ex.Response == null) throw;
            using (var response = ex.Response)
            using (var reader = new StreamReader(response.GetResponseStream())) {
                string body = reader.ReadToEnd();
                string message = ex.Message;
                try { var detail = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(body); if (detail.ContainsKey("message")) message = Convert.ToString(detail["message"]); } catch { }
                throw new IOException(message, ex);
            }
        }
    }

    private void SetControls(bool enabled)
    {
        openButton.Enabled = backupButton.Enabled = stopButton.Enabled = enabled;
        importButton.Enabled = enabled && samplesAvailable && !samplesImported;
        deleteButton.Enabled = enabled && samplesImported;
    }

    private async Task ApplyInstallOptions()
    {
        string options = Path.Combine(Program.AppHome, "installation.ini");
        if (!File.Exists(options)) return;
        string requestId = "";
        bool import = false;
        foreach (string line in File.ReadAllLines(options)) {
            if (line.StartsWith("RequestId=")) requestId = line.Substring(10).Trim();
            if (line.Trim() == "ImportSample=1") import = true;
        }
        string applied = Path.Combine(Program.DataHome, "last-install-request.txt");
        if (requestId.Length == 0 || (File.Exists(applied) && File.ReadAllText(applied).Trim() == requestId)) return;
        if (import) {
            status.Text = "正在导入安装时选择的测试数据，请稍候……";
            await Task.Run(() => Request("/desktop/sample-data/import", true));
        }
        File.WriteAllText(applied, requestId);
        status.Text = "系统已启动：" + url + "\n关闭此窗口后仍可在浏览器中使用。";
    }

    private async Task RefreshSamples()
    {
        string json = await Task.Run(() => Request("/desktop/sample-data", false));
        var info = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(json);
        samplesImported = Convert.ToBoolean(info["imported"]);
        samplesAvailable = Convert.ToBoolean(info["available"]);
        sampleStatus.Text = samplesImported
            ? "测试数据已导入：职业卫生项目 " + info["projects"] + " 个，放射案例 " + info["cases"] + " 个\n来源：线上版本数据快照；可按需删除。"
            : "尚未导入测试数据。可导入安装包中的线上版本数据快照。";
    }

    private async Task ChangeSamples(bool import)
    {
        if (sampleBusy || stopping || !ready) return;
        if (!import && MessageBox.Show(this, "将删除通过本功能导入的测试记录和附件。\n后来独立录入的业务记录会保留；如存在引用关系，会取消删除并提示。\n\n是否继续？", "删除测试数据", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
        sampleBusy = true;
        SetControls(false);
        status.Text = import ? "正在导入测试数据……" : "正在删除测试数据……";
        try {
            string result = await Task.Run(() => Request(import ? "/desktop/sample-data/import" : "/desktop/sample-data/delete", true));
            await RefreshSamples();
            status.Text = "系统已启动：" + url + "\n" + (import ? "测试数据已导入，请刷新浏览器。" : "测试数据已删除，请刷新浏览器。");
            var info = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(result);
            if (info.ContainsKey("pendingFiles") && Convert.ToInt32(info["pendingFiles"]) > 0)
                MessageBox.Show(this, "部分测试附件仍被其他程序占用，业务记录已删除。请关闭相关文档后再清理数据目录中的测试附件。", Text);
        } catch (Exception ex) {
            status.Text = "操作未完成，系统仍在运行：" + url;
            MessageBox.Show(this, ex.Message, Text, MessageBoxButtons.OK, MessageBoxIcon.Warning);
        } finally { sampleBusy = false; SetControls(true); }
    }

    private async Task StopServer()
    {
        if (server == null || server.HasExited) return;
        await Task.Run(delegate
        {
            // Allow initialization to finish before asking Spring to flush and close H2.
            for (int i = 0; i < 60 && !server.HasExited; i++)
            {
                try { Request("/desktop/shutdown", true); break; }
                catch { Thread.Sleep(500); }
            }
            if (!server.WaitForExit(45000)) throw new IOException("服务仍在保存数据，请稍后再退出。");
            server.WaitForExit();
        });
        ready = false;
    }

    private async Task ExitApplication()
    {
        if (stopping || sampleBusy) return;
        stopping = true;
        SetControls(false);
        status.Text = "正在保存数据并停止服务……";
        try { await StopServer(); canClose = true; Close(); }
        catch (Exception ex) { stopping = false; stopButton.Enabled = true; status.Text = ex.Message; }
    }

    private void SaveManual(string fileName, string title)
    {
        try
        {
            string source = Path.Combine(Program.AppHome, "web", "manuals", fileName);
            if (!File.Exists(source)) throw new FileNotFoundException("安装文件中缺少操作手册，请重新安装最新版程序。");
            using (var dialog = new SaveFileDialog {
                Title = "保存" + title,
                Filter = "Word 文档 (*.docx)|*.docx",
                DefaultExt = "docx",
                AddExtension = true,
                OverwritePrompt = true,
                FileName = "职业危害与放射诊疗检测数据平台-" + title + ".docx"
            })
            {
                if (dialog.ShowDialog(this) != DialogResult.OK) return;
                string destination = Path.GetFullPath(dialog.FileName);
                if (!string.Equals(Path.GetFullPath(source), destination, StringComparison.OrdinalIgnoreCase))
                    File.Copy(source, destination, true);
                MessageBox.Show(this, "手册已保存：\n" + destination + "\n\n可使用 Word 或 WPS 打开阅读。", title, MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }
        catch (Exception ex) { MessageBox.Show(this, "保存手册失败：" + ex.Message, Text, MessageBoxButtons.OK, MessageBoxIcon.Warning); }
    }

    private async Task Backup()
    {
        using (var dialog = new SaveFileDialog { Filter = "数据备份 (*.zip)|*.zip", FileName = "BHFYZX-备份-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".zip" })
        {
            if (dialog.ShowDialog(this) != DialogResult.OK) return;
            string destination = Path.GetFullPath(dialog.FileName);
            if (destination.StartsWith(Program.DataHome.TrimEnd('\\') + "\\", StringComparison.OrdinalIgnoreCase))
            { MessageBox.Show(this, "请将备份保存到数据目录以外的位置。", Text); return; }
            stopping = true;
            SetControls(false);
            status.Text = "正在暂停服务并备份，请勿关闭窗口……";
            try
            {
                await StopServer();
                string temporary = destination + "." + Guid.NewGuid().ToString("N") + ".tmp";
                try
                {
                    await Task.Run(() => ZipFile.CreateFromDirectory(Program.DataHome, temporary, CompressionLevel.Optimal, false));
                    if (File.Exists(destination)) File.Delete(destination);
                    File.Move(temporary, destination);
                }
                finally { if (File.Exists(temporary)) File.Delete(temporary); }
                MessageBox.Show(this, "备份已保存：\n" + destination, Text);
            }
            catch (Exception ex) { MessageBox.Show(this, "备份失败：" + ex.Message, Text); }
            finally { stopping = false; }
            if (server == null || server.HasExited) await StartServer();
            else { SetControls(true); status.Text = "系统仍在运行：" + url; }
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            if (stopWait != null) stopWait.Unregister(null);
            if (openWait != null) openWait.Unregister(null);
            stopEvent.Dispose(); openEvent.Dispose(); tray.Dispose();
            if (server != null) server.Dispose();
        }
        base.Dispose(disposing);
    }
}
