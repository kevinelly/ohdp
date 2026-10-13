package com.dsf.radiology.service;

import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.config.RadiologyStorageProperties;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Isolated office profiles avoid cross-request locks and keep previews out of report archives. */
@Service
@RequiredArgsConstructor
public class RadiologyPdfPreviewService {
    private final RadiologyStorageProperties storage;
    private final Semaphore slots=new Semaphore(2);
    public boolean available(){return Files.isExecutable(Path.of("/usr/bin/soffice"));}
    public byte[] convert(byte[] source) {
        if(!available())throw new BusinessException("服务器未安装报告分页预览组件");
        if(!slots.tryAcquire())throw new BusinessException("报告预览繁忙，请稍后重试");
        Path temp=null;Process process=null;
        try {
            Path root=Path.of(storage.getBaseDir()).toAbsolutePath().normalize().resolve("preview");Files.createDirectories(root);
            temp=Files.createTempDirectory(root,"report-");Path input=temp.resolve("report.docx");Files.write(input,RadiologyPdfPagination.prepare(source));
            process=new ProcessBuilder("/usr/bin/soffice","-env:UserInstallation="+temp.resolve("office").toUri(),"--headless","--convert-to","pdf:writer_pdf_Export","--outdir",temp.toString(),input.toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("conversion.log").toFile()).start();
            if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new BusinessException("报告预览超时，请稍后重试");}
            Path output=temp.resolve("report.pdf");if(process.exitValue()!=0 || !Files.isRegularFile(output))throw new BusinessException("报告分页预览转换失败");
            byte[] pdf=Files.readAllBytes(output);if(pdf.length<5 || pdf[0]!='%' || pdf[1]!='P')throw new BusinessException("报告预览文件无效");return RadiologyPdfPagination.number(pdf);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new BusinessException("报告预览已中断");}
        catch(java.io.IOException ex){throw new BusinessException("报告预览服务异常");}
        finally {
            if(process!=null && process.isAlive())process.destroyForcibly();
            if(temp!=null)try(var paths=Files.walk(temp)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}});}catch(java.io.IOException ignored){}
            slots.release();
        }
    }
}
