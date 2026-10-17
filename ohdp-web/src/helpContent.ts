export type HelpTopicId =
  | 'overview'
  | 'account'
  | 'project'
  | 'report'
  | 'radiology-dr'
  | 'radiology-ct'
  | 'maintenance'
  | 'basic-data'
  | 'hazard'
  | 'configuration'
  | 'laboratory'
  | 'feedback'
  | 'system'
  | 'data-quality'
  | 'troubleshooting'

export interface HelpField {
  name: string
  description: string
  required?: string
}

export interface HelpSection {
  title: string
  paragraphs?: string[]
  steps?: string[]
  fields?: HelpField[]
  notes?: string[]
}

export interface HelpTopic {
  id: HelpTopicId
  category: string
  title: string
  summary: string
  audience: string
  keywords: string[]
  sections: HelpSection[]
  related: HelpTopicId[]
}

export const helpTopics: HelpTopic[] = [
  {
    id: 'overview',
    category: '开始使用',
    title: '系统概览与业务流程',
    summary: '了解系统用途、主要角色以及从建档到报告归档的完整流转顺序。',
    audience: '检测人员、报告人员、管理员',
    keywords: ['首页', '流程', '建档', '录入', '计算', '复核', '报告'],
    sections: [
      {
        title: '系统用途',
        paragraphs: [
          '本系统用于统一管理职业危害与放射诊疗检测项目，将原始数据、计算结果、标准判定、审核批准和 Word 报告保存在同一条可追溯记录中。',
          '页面采用样品流转台结构。左侧选择功能，中间处理当前业务，页面顶部显示当前模块与连接状态。',
        ],
      },
      {
        title: '标准五阶段',
        steps: [
          '项目设置：创建或选择项目，录入受检单位、检测类别和人员信息。',
          '数据录入：补充岗位、设备、检测条件和原始检测数据，必要时使用 Excel 模板批量导入。',
          '自动计算：系统按已配置的公式、限值和标准版本生成中间值、最终值与初步判定。',
          '待复核：检测人员核对异常与缺项，具有权限的人员完成审核和批准。',
          '报告生成：通过预检后生成 Word 报告，下载并归档，系统同时保留操作日志。',
        ],
      },
      {
        title: '如何理解页面导航',
        fields: [
          { name: '左侧功能菜单', description: '表示“要去哪个业务模块”。切换菜单会更换整个工作页面；系统管理固定在帮助中心之前，且仅管理员可见。', required: '导航' },
          { name: '顶部五阶段', description: '表示当前记录“已经做到哪一步”，用于理解业务进度，不代替左侧功能菜单。', required: '进度提示' },
          { name: '页面内标签页', description: '表示“正在查看当前记录的哪个部分”，例如基本信息、检测数据、报告与附件。', required: '记录内部切换' },
          { name: '帮助问号', description: '点击后先在右侧查看当前字段的简要说明；点击“查看完整帮助”会在新标签页打开详细内容。', required: '随时可用' },
        ],
        notes: ['“项目工作台”和“报告管理”是两个独立工作页面；二者通过左侧菜单切换，不只是更换页面下方标签。'],
      },
      {
        title: '页面通用操作',
        fields: [
          { name: '查询', description: '按编号、名称或状态筛选当前列表。', required: '可选' },
          { name: '新增或创建', description: '进入新的业务记录，保存前先核对必填项。', required: '按业务要求' },
          { name: '编辑', description: '载入现有记录并修改。已审批或已归档数据可能受到权限限制。', required: '按权限' },
          { name: '刷新', description: '重新读取服务器数据，不会保存当前未提交内容。', required: '可选' },
          { name: '状态标签', description: '显示记录所处阶段。状态文字是判断依据，颜色只用于辅助区分。', required: '只读' },
        ],
        notes: [
          '较长的放射案例建档表单会自动在当前浏览器保存草稿，并在下次新建同类案例时恢复；编辑既有案例仍需点击“保存修改”。',
          '按钮不可用时先查看“下一步操作”或“当前限制”，系统会说明需要先选择记录、补全数据、取得权限或推进业务状态。',
          '涉及判定或报告的修改应重新计算、重新预检并按流程复核。',
        ],
      },
    ],
    related: ['project', 'radiology-dr', 'radiology-ct', 'report'],
  },
  {
    id: 'account',
    category: '开始使用',
    title: '登录、会话与账号安全',
    summary: '说明账号登录、保持登录、退出系统和常见登录失败处理。',
    audience: '所有用户',
    keywords: ['登录', '密码', '保持登录', '退出', '权限'],
    sections: [
      {
        title: '登录步骤',
        steps: [
          '在登录页输入管理员分配的账号和密码。',
          '如为固定办公电脑，可勾选“保持登录 30 天”；公共电脑不建议勾选。',
          '点击“登陆”。登录成功后，系统按账号角色显示可用菜单和操作。',
        ],
      },
      {
        title: '安全说明',
        paragraphs: ['保持登录只保存会话信息，不在系统中保存明文密码。完成工作后可点击右上角“退出系统”立即结束会话。'],
        notes: ['连续出现登录失败时，先确认大小写和输入法，再联系管理员核对账号状态或重置密码。不要多人共用同一账号。'],
      },
    ],
    related: ['system', 'troubleshooting'],
  },
  {
    id: 'project',
    category: '项目与报告',
    title: '项目工作台',
    summary: '创建项目，维护受检信息、岗位与检测结果，并完成计算、判定和报告预检。',
    audience: '检测人员、报告人员',
    keywords: ['项目', '岗位', '检测结果', '批量录入', '自动判定', '预检'],
    sections: [
      {
        title: '新建与选择项目',
        steps: [
          '点击“创建项目”，录入受检单位、检测类别、采样日期和报告相关人员。',
          '保存项目后，在项目列表中选择该记录。列表默认按创建时间倒序排列。',
          '如项目内容相近，可使用“复制”建立新项目，再逐项核对日期、单位、岗位和检测数据。',
        ],
      },
      {
        title: '四类项目数据',
        fields: [
          { name: '项目信息', description: '项目编号、单位、地址、检测类别、日期和备注。', required: '核心字段必填' },
          { name: '报告补充', description: '任务由来、评价依据、编写人、审核人、签发人及证书信息。', required: '生成报告前必填' },
          { name: '岗位信息', description: '岗位、班制、工时、人数、作业内容、危害因素和防护措施。', required: '按项目实际填写' },
          { name: '检测结果', description: '因素、指标类型、原始值、单位、限值和计算所需参数。', required: '判定前必填' },
        ],
      },
      {
        title: '推荐操作顺序',
        steps: [
          '完成项目信息并手动保存。',
          '录入岗位，再将检测结果关联到正确岗位。',
          '核对单位、原始值、限值和计算参数，执行自动计算。',
          '处理所有缺项和异常，检查判定依据。',
          '执行报告预检，确认无阻断项后进入报告管理。',
        ],
        notes: ['原始值、公式版本、标准依据和人工修改会影响报告结论。修改后必须重新计算，不能沿用旧结论。'],
      },
    ],
    related: ['report', 'maintenance', 'configuration', 'data-quality'],
  },
  {
    id: 'report',
    category: '项目与报告',
    title: '报告管理',
    summary: '跨项目检索报告，执行预检、预览、生成、下载、修订和归档。',
    audience: '报告人员、审核人员',
    keywords: ['报告', 'Word', '预览', '预检', '下载', '归档', '日志'],
    sections: [
      {
        title: '生成报告',
        steps: [
          '按项目编号、单位名称或报告状态查询项目。',
          '选择项目并执行报告预检，逐项处理缺失的单位、岗位、结果、人员和标准依据。',
          '打开报告预览，核对封面信息、检测表格、判定结论和签字人员。',
          '确认无误后生成 Word 报告。报告生成记录会保存生成时间、操作人和文件名。',
          '下载文件进行最终检查。需要修改时回到数据来源页面修正，再重新生成报告。',
        ],
      },
      {
        title: '覆盖与修订',
        paragraphs: ['每个项目仅保留当前报告文件。重新生成会覆盖当前文件，但操作日志会记录本次生成行为。应先确认数据、状态和人员信息已更新。'],
        notes: ['不要直接修改下载后的 Word 作为系统最终数据。需要永久保留的修订应先改系统数据，再重新生成。'],
      },
      {
        title: '2026 北海定期检测报告补充内容',
        paragraphs: [
          '职业病危害因素定期检测报告使用《（2026）定期检测报告模板-北海》的版式、章节、表格和报告单。模板中的红色字体是编制规则，不会作为正式报告正文输出。',
          '“报告补充”按报告章节保存单位基本情况、原辅材料、设备、防护设施、个人防护用品、生产工艺、采样计划、现场条件、风险分类、问题和建议。',
        ],
        steps: [
          '先填写用人单位基本信息，统一社会信用代码、负责人和行业三级分类必须与客户资料一致。',
          '逐条填写原辅材料、生产设备、防护设施和个人防护用品；确实不涉及时，只能在核实后勾选“不涉及确认”，系统不会自动判断。',
          '明确 VOC 定性、排除性检测和辐射源项的实际开展方式。未开展、另行委托或另出报告均需留下确认说明。',
          '填写现场采样与测量计划、生产状态、气象条件、检测人员和计划变更说明。',
          '填写风险分类、主要危害、超标情况、存在问题和针对性建议；即使无超标也需要明确写明。',
          '核对采样编号、日期、时间、仪器编号和检测条件等原始记录字段，执行报告预检。所有阻断项处理后才能生成正式报告。',
        ],
        notes: [
          '系统不删除尚未开展或尚未确认的报告章节，也不把空白内容自动写成“不涉及”。缺项会在报告预检中逐项列出。',
          '模板红字中的二选一、三选一和条件性表格必须由业务人员依据客户资料确认，不能由系统猜测。',
        ],
      },
      {
        title: '报告如何追溯到数据与依据',
        fields: [
          { name: '业务主索引', description: '项目编号或 DR/CT 案例编号用于关联受检单位、设备、原始数据和报告文件。', required: '系统记录' },
          { name: '计算与规则版本', description: '放射报告保留计算版本、规则判定结果和标准文号；职业危害报告使用项目当前确认的计算结果和检测方法。', required: '生成前核对' },
          { name: '审批信息', description: '编写、审核、批准人员及意见跟随业务状态进入报告或报告快照。', required: '按流程' },
          { name: '文件与操作记录', description: '系统记录文件名、生成时间、操作人；放射报告额外记录 SHA-256，用于核对文件是否被改变。', required: '自动记录' },
        ],
        notes: ['报告结论不是从 Word 反算得出，而是由系统已保存的数据、公式、限值和适用标准共同决定。'],
      },
    ],
    related: ['project', 'radiology-dr', 'radiology-ct', 'data-quality'],
  },
  {
    id: 'radiology-dr',
    category: '放射诊疗检测',
    title: 'DR 设备质量控制',
    summary: '依据 WS 76-2020 和 GBZ 130-2020 完成 DR 案例建档、录入、计算、复核和报告。',
    audience: '放射检测人员、审核人员、报告人员',
    keywords: ['DR', 'WS 76', '防护', '性能', 'Excel', '案例'],
    sections: [
      {
        title: '案例建档',
        fields: [
          { name: '受检机构与设备', description: '机构名称、地址、设备厂家、型号、编号和安装场所。', required: '必填' },
          { name: '检测条件', description: '检测日期、环境条件、曝光条件、仪器及校准信息。', required: '按项目填写' },
          { name: '标准版本', description: '设备质量控制采用 WS 76-2020，场所防护采用 GBZ 130-2020。', required: '系统记录' },
        ],
      },
      {
        title: '四步建档操作',
        steps: [
          '填写受检单位与案例信息。案例编号和受检单位为必填项，系统会在进入下一步前定位缺项。',
          '填写设备与模体信息。DR 至少填写设备名称；CT 还应按实际资料填写模体厂家、型号、编号和说明书依据。',
          '填写检测日期、检测依据、检测仪器及证书，以及编写、审核和批准人员。',
          '在确认页核对关键字段，然后创建案例。新建过程中内容每隔短时间自动保存为本机草稿，也可手动点击“保存草稿”。',
        ],
        notes: ['本机草稿只保存在当前浏览器，不等于服务器正式记录；换电脑、清理浏览器数据或无痕模式可能无法恢复。'],
      },
      {
        title: '录入与导入',
        steps: [
          '创建 DR 案例并保存基本信息。',
          '逐项录入设备性能检测数据，或下载当前系统模板后批量导入 Excel。',
          '录入场所防护点位、位置描述、测量值及必要的工作负荷参数。',
          '执行计算并检查每项结果、限值、结论和异常提示。',
          '提交审核，审核通过后由有权限人员批准并生成报告。',
        ],
        notes: ['Excel 导入必须使用系统当前模板。不要改变工作表名称、列名或单元格结构。导入后仍需在页面逐项核对。'],
      },
      {
        title: '防护计算提示',
        paragraphs: ['防护点测量值超过当前规则阈值时，系统会继续使用年工作负荷、居留因子和使用因子计算年有效剂量。最终结论同时受原始测量值、计算参数和规则版本影响。'],
      },
    ],
    related: ['radiology-ct', 'report', 'basic-data', 'data-quality'],
  },
  {
    id: 'radiology-ct',
    category: '放射诊疗检测',
    title: 'CT 设备质量控制',
    summary: '依据 WS 519-2019 和 GBZ 130-2020 完成 CT 性能、防护、稳定性和报告流程。',
    audience: '放射检测人员、审核人员、报告人员',
    keywords: ['CT', 'CTDIw', '头模', '体模', '稳定性', 'WS 519'],
    sections: [
      {
        title: '关键基础信息',
        fields: [
          { name: 'CT 设备', description: '厂家、型号、设备编号、安装场所和主要配置。', required: '必填' },
          { name: '检测模体', description: '模体厂家、型号、类型和说明书或技术资料。', required: '按检测项填写' },
          { name: '厂家规定值', description: '分别录入头模和体模的 CTDIw 厂家规定值，并注明来源。', required: '涉及 CTDIw 时必填' },
          { name: '稳定性基线', description: '录入设备验收或首次稳定性检测确定的基线值和日期。', required: '稳定性判定前必填' },
        ],
      },
      {
        title: '推荐操作顺序',
        steps: [
          '创建 CT 案例，先完成设备、模体、厂家规定值和稳定性基线。',
          '逐项录入性能检测和场所防护数据，或使用系统当前 Excel 模板导入。',
          '执行计算，核对 CTDIw、偏差、稳定性结果、限值和依据。',
          '处理缺项与异常后提交审核，按权限完成批准。',
          '预览并生成报告，确认头模、体模、基线和结论均进入对应报告位置。',
        ],
        notes: ['厂家规定值和稳定性基线不能由系统随意推定。数值应来自设备资料、验收记录或经确认的历史基线，并保留来源说明。'],
      },
    ],
    related: ['radiology-dr', 'report', 'basic-data', 'data-quality'],
  },
  {
    id: 'maintenance',
    category: '单位、人员与业务模板',
    title: '单位、人员与业务模板',
    summary: '维护受检单位和人员资质，以及岗位、危害因素、字典和报告文本等可复用业务模板。',
    audience: '业务管理员、报告人员',
    keywords: ['单位', '岗位模板', '危害因素模板', '字典', '人员资质', '报告文本'],
    sections: [
      {
        title: '功能说明',
        fields: [
          { name: '受检单位', description: '维护受检单位名称、地址、联系人及联系方式。', required: '按实际维护' },
          { name: '岗位模板', description: '保存常用岗位、班制、工时、危害因素和防护措施。', required: '可选复用' },
          { name: '岗位危害因素模板', description: '保存因素、指标、单位和默认限值，减少重复录入。', required: '按项目类型维护' },
          { name: '业务字典', description: '统一系统枚举值和中文显示名称。', required: '管理员维护' },
          { name: '人员资质', description: '维护编写、审核、签发人员及证书编号。', required: '报告生成前' },
          { name: '报告文本模板', description: '维护任务由来、评价依据等可复用正式文本。', required: '按报告要求' },
        ],
        notes: ['修改模板不会自动改写已保存项目。需要更新历史项目时，应进入项目核对并重新保存。'],
      },
    ],
    related: ['project', 'report', 'configuration'],
  },
  {
    id: 'basic-data',
    category: '检测基础库',
    title: '检测基础库与依据文件',
    summary: '管理法律法规、标准规范、行政区划、检测仪器设备、危害因素和 SDS，并保留依据文件及版本信息。',
    audience: '业务管理员、质量负责人',
    keywords: ['法律法规', '标准规范', '文件', '版本', '地区', '设备'],
    sections: [
      {
        title: '法规和标准',
        steps: [
          '新增法规或标准，填写正式名称、文号、级别、发布日期、实施日期和状态。',
          '上传合法取得的原始文件或机构内部受控副本，记录文件版本和来源。',
          '法规修订或废止时新增版本或更新状态，不建议直接覆盖历史依据。',
          '在检测方法、计算规则或报告模板中引用对应记录，确保结论可追溯。',
        ],
        notes: ['建议导入相关文档。文档用于查阅和审计，系统公式与判定规则仍应独立配置并经过确认，不能仅靠上传文件自动生效。'],
      },
      {
        title: '行政区划和检测仪器设备',
        fields: [
          { name: '行政区划', description: '维护北海市及下属行政区域等标准化地区信息。', required: '按业务范围维护' },
          { name: '检测仪器设备', description: '维护采样与实验仪器名称、型号、编号、状态及备注。', required: '实际使用设备必填' },
        ],
      },
    ],
    related: ['configuration', 'radiology-dr', 'radiology-ct', 'data-quality'],
  },
  {
    id: 'hazard',
    category: '检测基础库',
    title: '危害因素与 SDS',
    summary: '维护职业危害因素、接触限值及化学品安全技术说明书。',
    audience: '检测人员、业务管理员',
    keywords: ['危害因素', '化学因素', '粉尘', '物理因素', 'SDS', 'CAS'],
    sections: [
      {
        title: '危害因素库',
        fields: [
          { name: '名称与分类', description: '使用中文分类名称，如化学因素、粉尘、物理因素。', required: '必填' },
          { name: 'CAS 号', description: '化学物质可填写 CAS 登记号，粉尘和物理因素按实际情况留空。', required: '条件必填' },
          { name: '限值与单位', description: '维护适用指标、限值、单位、标准来源和版本。', required: '用于判定时必填' },
        ],
      },
      {
        title: 'SDS 说明书',
        steps: [
          '选择已维护的化学危害因素。',
          '填写中文名称、供应商、版本或修订日期。',
          '上传 SDS 原始文件并保存。',
          '定期检查文件是否仍与现场使用产品一致。',
        ],
      },
    ],
    related: ['maintenance', 'project', 'basic-data'],
  },
  {
    id: 'configuration',
    category: '业务规则与模板',
    title: '检测方法、分类与机构编号规则',
    summary: '维护检测方法、公式、标准依据、中文分类以及机构和编号规则。',
    audience: '业务管理员、质量负责人',
    keywords: ['检测方法', '公式', '分类', '机构', '编号规则'],
    sections: [
      {
        title: '检测方法',
        fields: [
          { name: '名称与代码', description: '用于业务选择和系统关联，代码应唯一且稳定。', required: '必填' },
          { name: '简称', description: '用于表格或报告中的紧凑显示。', required: '建议填写' },
          { name: '计算公式', description: '记录经确认的计算表达式或公式标识。', required: '涉及自动计算时必填' },
          { name: '检测依据', description: '填写标准文号，多个依据按系统约定分隔。', required: '必填' },
        ],
        notes: ['公式调整会影响新计算结果。修改前应确认业务含义、单位、舍入规则、边界条件和版本生效范围，并完成验证。'],
      },
      {
        title: '分类与机构编号规则',
        paragraphs: ['检测分类用于统一页面和报告中的中文分类名称及顺序。机构与编号规则用于报告抬头、联系方式、资质证书编号以及样品和报告编号前缀。'],
      },
    ],
    related: ['basic-data', 'project', 'laboratory', 'data-quality'],
  },
  {
    id: 'laboratory',
    category: '实验检测',
    title: '实验任务与结果计算复核',
    summary: '创建实验任务单，完成领取、提交、参数核对和结果计算。',
    audience: '实验人员、检测人员、审核人员',
    keywords: ['实验', '任务单', '领取', '提交', '曲线', '结果计算'],
    sections: [
      {
        title: '任务流转',
        steps: [
          '创建任务单并关联正确项目，填写签发人和备注。',
          '实验人员领取待处理任务，确认样品和项目对应关系。',
          '完成实验和原始记录后提交审核。',
          '在结果计算中核对曲线参数、稀释倍数、单位和最终结果。',
          '结果确认后回到项目工作台检查关联数据和报告结论。',
        ],
        notes: ['项目 ID、样品标识和任务单号必须一致。发现错配时先停止计算并由管理员核对关联关系。'],
      },
    ],
    related: ['project', 'configuration', 'data-quality'],
  },
  {
    id: 'feedback',
    category: '意见与支持',
    title: '意见提交与处理跟踪',
    summary: '提交功能意见、问题和改进建议，并通过编号、处理安排和时间线跟踪全过程。',
    audience: '所有用户、意见处理人员、管理员',
    keywords: ['意见反馈', '自动采纳', '处理进度', '附件', '时间线', '关闭'],
    sections: [
      {
        title: '提交一条可处理的意见',
        steps: [
          '进入“意见反馈”，点击“提交新意见”。',
          '选择分类和紧急程度，用明确标题概括问题或建议。',
          '在详细说明中依次写明当前现象、期望结果、操作步骤和影响范围。',
          '如意见对应具体项目、DR/CT 案例或报告，填写关联编号；必要时添加截图或资料。',
          '点击“提交并自动采纳”。系统生成 FK 开头的跟踪编号，并记录提交及自动采纳两条时间线。',
        ],
        notes: ['紧急程度用于处理排序，不表示系统故障等级；涉及安全、数据丢失或报告错误时，应同时按机构应急流程通知负责人。'],
      },
      {
        title: '状态含义',
        fields: [
          { name: '已采纳', description: '意见已进入处理队列，尚未开始具体处理。', required: '系统自动设置' },
          { name: '处理中', description: '处理人员已领取或正在实施、核对。', required: '处理人员更新' },
          { name: '待补充', description: '当前信息不足，提交人需要补充说明或附件。', required: '提交人待办' },
          { name: '待用户确认', description: '处理人员已给出结果，等待提交人确认关闭或要求重新处理。', required: '提交人待办' },
          { name: '已关闭', description: '处理已经完成并确认，时间线继续保留。', required: '结束状态' },
          { name: '不予采纳/已撤回', description: '处理人员说明不采纳原因，或提交人在处理前撤回。', required: '结束状态' },
        ],
      },
      {
        title: '处理工作台',
        steps: [
          '具有意见处理权限的账号进入“处理工作台”查看全部意见，或使用“分配给我”聚焦本人任务。',
          '领取意见，设置优先级、计划完成日期和计划版本。',
          '需要更多资料时转为“待补充”并写明具体缺少内容。',
          '处理完成后填写处理结果，转为“待用户确认”。',
          '提交人确认后关闭；如仍有问题，应填写原因并恢复处理。',
        ],
        notes: ['“内部处理记录”只对意见处理人员可见；普通补充说明对提交人和处理人员均可见。所有状态变化都会保留操作人和时间。'],
      },
      {
        title: '附件与隐私',
        paragraphs: ['支持 PNG、JPG、WEBP、PDF、Word 和 Excel，单个文件不超过 10 MB。附件用于说明问题，不替代系统业务数据。'],
        notes: ['不要上传账号密码、身份证号或与问题无关的敏感资料。结束状态下不能继续增删附件。'],
      },
    ],
    related: ['overview', 'troubleshooting', 'system'],
  },
  {
    id: 'system',
    category: '系统管理',
    title: '用户、角色与系统管理',
    summary: '管理员维护账号、角色、权限、组织、通知、日志和系统参数。',
    audience: '系统管理员',
    keywords: ['用户', '角色', '权限', '菜单', '部门', '岗位', '通知', '日志', '参数'],
    sections: [
      {
        title: '权限管理原则',
        paragraphs: ['用户通过角色获得菜单和操作权限。放射检测的审核、批准、报告生成和规则维护属于独立权限，应按岗位职责分配。'],
        fields: [
          { name: '用户管理', description: '创建账号、设置角色和启用状态。', required: '管理员' },
          { name: '角色与权限', description: '配置可见菜单和可执行操作，遵循最小权限原则。', required: '管理员' },
          { name: '部门与岗位', description: '维护组织结构和人员岗位。', required: '按机构实际' },
          { name: '通知公告', description: '发布内部业务提醒。', required: '可选' },
          { name: '操作日志', description: '查询关键操作、时间和账号，用于追溯。', required: '只读审计' },
          { name: '参数配置', description: '维护系统运行参数，修改前应明确影响范围。', required: '管理员' },
        ],
        notes: ['不要删除仍被历史记录引用的角色、人员或基础配置。先停用并确认影响，再按维护流程处理。'],
      },
    ],
    related: ['account', 'data-quality', 'troubleshooting'],
  },
  {
    id: 'data-quality',
    category: '安全与排查',
    title: '数据核对与质量控制',
    summary: '用统一检查清单保证录入、计算、判定和报告内容同源且可追溯。',
    audience: '检测人员、审核人员、报告人员、管理员',
    keywords: ['质量', '核对', '追溯', '原始值', '公式版本', '备份'],
    sections: [
      {
        title: '提交前检查清单',
        steps: [
          '核对项目、单位、设备、岗位、样品和检测日期是否对应。',
          '核对原始值、单位、小数位、检出限和缺失值表达。',
          '确认公式、限值、标准及其版本适用于当前检测日期和设备。',
          '检查系统提示的缺项、异常、待评估和不合格项目。',
          '确认编写、审核、签发人员及资质信息准确。',
          '生成报告后逐页检查封面、表格、结论、依据和签字区域。',
        ],
      },
      {
        title: '修改与追溯',
        paragraphs: ['业务数据修改后应重新计算和重新生成报告。系统日志用于确认操作人和时间，报告文件不是替代系统数据的唯一依据。'],
        notes: ['部署或升级时不得删除数据库卷、报告卷、法规文件卷和放射检测数据卷。应定期执行离线备份并验证恢复。'],
      },
    ],
    related: ['project', 'report', 'troubleshooting'],
  },
  {
    id: 'troubleshooting',
    category: '安全与排查',
    title: '常见问题与处理',
    summary: '处理登录、数据加载、计算、Excel 导入、报告和文件打开等常见问题。',
    audience: '所有用户',
    keywords: ['失败', '错误', '导入失败', '打不开', '无数据', '计算异常'],
    sections: [
      {
        title: '常见现象',
        fields: [
          { name: '登录失败', description: '检查账号、密码、大小写和账号状态；必要时由管理员重置密码。' },
          { name: '列表无数据', description: '清空查询条件后刷新，并确认账号权限和当前模块。' },
          { name: 'Excel 导入失败', description: '重新下载系统当前模板，核对工作表、列名、数据类型和必填单元格。' },
          { name: '计算结果异常', description: '核对原始值、单位、基线、厂家规定值、公式版本和标准限值。' },
          { name: '无法生成报告', description: '先执行预检，补齐阻断项并确认当前状态和操作权限。' },
          { name: '无法打开文件夹', description: '线上 Linux 容器不能直接打开 Windows 资源管理器，应使用下载功能获取文件。' },
        ],
      },
      {
        title: '反馈问题时需要提供',
        steps: [
          '记录发生时间、账号和当前功能。',
          '记录项目编号或案例编号，不要在公共渠道发送密码。',
          '截图完整错误提示，并说明触发错误前的操作顺序。',
          '如涉及导入，保留原始模板副本；如涉及报告，保留对应报告文件名。',
        ],
      },
    ],
    related: ['account', 'data-quality', 'system'],
  },
]

export interface HelpAnchor {
  topicId: HelpTopicId
  title: string
  summary: string
  source?: string
  example?: string
  calculationImpact?: string
  reportImpact?: string
  cautions?: string[]
  keywords: string[]
}

export const helpAnchors = {
  'session.remember': {
    topicId: 'account', title: '保持登录 30 天',
    summary: '仅在当前浏览器保存会话信息，不保存明文密码。适合固定办公电脑使用。',
    cautions: ['公共电脑不要勾选。离岗或账号异常时应主动退出系统。'],
    keywords: ['保持登录', '密码', '会话', '30天'],
  },
  'workflow.stages': {
    topicId: 'overview', title: '五阶段业务流转',
    summary: '项目设置、数据录入、自动计算、待复核和报告生成依次推进。当前阶段由项目状态和完整性共同决定。',
    calculationImpact: '原始数据变更后应退回计算阶段重新计算。',
    reportImpact: '只有完成计算、复核和预检的数据才能形成受控报告。',
    keywords: ['五阶段', '流程', '状态', '下一步'],
  },
  'navigation.structure': {
    topicId: 'overview', title: '左侧菜单、五阶段和页面标签的区别',
    summary: '左侧菜单选择业务模块，顶部五阶段表示当前记录进度，页面内标签只切换当前记录的内容部分。',
    example: '从“项目工作台”进入“报告管理”应使用左侧菜单；查看同一项目的岗位信息则使用页面内标签。',
    keywords: ['导航', '菜单', '五阶段', '标签页'],
  },
  'form.autosave': {
    topicId: 'overview', title: '长表单草稿自动保存',
    summary: '新建 DR/CT 案例时，系统会把未提交内容定时保存在当前浏览器，并在下次新建同类案例时恢复。',
    cautions: ['本机草稿不等于服务器记录。创建成功后草稿会清除；换电脑、无痕模式或清除浏览器数据后可能无法恢复。'],
    keywords: ['自动保存', '草稿', '长表单', '恢复'],
  },
  'form.disabled-reason': {
    topicId: 'overview', title: '按钮不可用的原因',
    summary: '操作不可用通常表示尚未选择记录、必填数据不完整、业务状态未到达或当前账号没有相应权限。',
    cautions: ['先查看右侧“下一步操作”和“当前限制”，不要通过重复刷新绕过流程。'],
    keywords: ['按钮不可用', '当前限制', '权限', '下一步'],
  },
  'status.chinese': {
    topicId: 'overview', title: '中文业务状态',
    summary: '页面统一显示草稿、已录入、已计算、待审核、已审核、已批准、已出报告等中文状态；系统内部代码不作为用户判断依据。',
    keywords: ['中文状态', '草稿', '待审核', '已批准'],
  },
  'project.create': {
    topicId: 'project', title: '创建和选择项目',
    summary: '先创建项目，再选择该项目继续录入岗位、检测结果和报告信息。',
    example: '创建后项目会出现在列表首行，选中后右侧操作区显示当前项目编号。',
    cautions: ['复制项目后必须重新核对单位、日期、岗位和检测数据。'],
    keywords: ['创建项目', '项目选择', '复制项目'],
  },
  'project.code': {
    topicId: 'project', title: '项目编号',
    summary: '项目编号由系统创建时生成，是报告、日志和数据关联的主要标识。',
    reportImpact: '项目编号会显示在报告文件名及报告内容中。',
    cautions: ['反馈问题时提供项目编号，但不要提供账号密码。'],
    keywords: ['项目编号', '编号规则', '文件名'],
  },
  'project.status': {
    topicId: 'project', title: '项目状态',
    summary: '草稿、已录入、已判定和已生成报告表示项目当前业务阶段。',
    calculationImpact: '已录入状态表示可以开始计算和判定。',
    reportImpact: '已生成报告表示当前数据已经形成报告文件。修改数据后需要重新生成。',
    keywords: ['项目状态', '草稿', '已录入', '已判定'],
  },
  'project.company': {
    topicId: 'project', title: '受检单位',
    summary: '从单位库选择实际受检机构，系统自动回填名称和地址。',
    example: '北海市海城区某医院。',
    reportImpact: '单位名称和地址进入报告封面及项目基本信息。',
    cautions: ['同名单位应通过地址和统一社会信用代码进一步确认。'],
    keywords: ['受检单位', '单位库', '单位地址'],
  },
  'project.detect-type': {
    topicId: 'project', title: '检测类别',
    summary: '检测类别决定项目所使用的数据模板、检测项目、判定方式和报告内容。',
    calculationImpact: '错误类别可能导致限值或计算规则不适用。',
    reportImpact: '类别名称进入报告，并决定报告表格结构。',
    cautions: ['开始录入前确认类别，已有大量数据后不要随意变更。'],
    keywords: ['检测类别', '字典', '分类'],
  },
  'project.dates': {
    topicId: 'project', title: '采样日期、报告日期和声明日期',
    summary: '采样日期表示现场或样品采集时间，报告日期表示报告形成时间，声明日期通常与报告日期一致。',
    reportImpact: '三个日期进入报告不同位置，并影响标准版本适用性判断。',
    cautions: ['不能仅因为系统默认当天就跳过核对。'],
    keywords: ['采样日期', '报告日期', '声明日期'],
  },
  'project.staff': {
    topicId: 'project', title: '编写、审核和签发人员',
    summary: '从人员资质库选择责任人员，系统同时带出证书编号。',
    reportImpact: '姓名和证书编号进入报告签字或责任信息区域。',
    cautions: ['应按实际职责选择，人员和证书必须在有效期内。'],
    keywords: ['编写人', '审核人', '签发人', '证书'],
  },
  'project.position': {
    topicId: 'project', title: '岗位信息',
    summary: '岗位记录班制、工时、人数、作业内容、危害因素和防护措施，是检测结果分组和评价的基础。',
    calculationImpact: '部分时间加权结果需要结合岗位工时。',
    reportImpact: '岗位和防护信息进入现场调查及检测结果表。',
    keywords: ['岗位', '班制', '工时', '作业内容'],
  },
  'project.factor-template': {
    topicId: 'project', title: '危害因素模板',
    summary: '模板用于带出因素名称、指标类型、单位和默认限值，减少重复录入。',
    calculationImpact: '模板中的指标和限值参与自动判定。',
    cautions: ['模板值只是录入起点，仍需核对当前项目适用标准。'],
    keywords: ['危害因素模板', '指标', '单位', '限值'],
  },
  'project.raw-value': {
    topicId: 'project', title: '检测原始值',
    summary: '填写仪器或实验原始记录中的值，保留原始表达并在数值字段中提供可计算值。',
    calculationImpact: '原始值是计算和判定的直接输入。',
    reportImpact: '最终值和结论可追溯到该原始值。',
    cautions: ['核对小数点、单位、检出限表达和空值。'],
    keywords: ['原始值', '数值', '检出限'],
  },
  'project.batch-paste': {
    topicId: 'project', title: '批量粘贴检测结果',
    summary: '按页面提供的列顺序从 Excel 复制多行数据，先预览识别结果，再确认写入。',
    cautions: ['不要包含合并单元格。写入前确认岗位、因素、单位和小数格式。'],
    keywords: ['批量粘贴', 'Excel', '多行数据'],
  },
  'project.auto-calc': {
    topicId: 'project', title: '自动计算与判定',
    summary: '系统根据原始值、检测方法、公式、单位、限值和标准版本生成计算结果与初步结论。',
    source: '计算规则来自系统检测方法、危害因素限值和对应标准配置。',
    calculationImpact: '修改任何输入后都应重新执行。',
    reportImpact: '报告使用最新一次计算和判定结果。',
    cautions: ['自动结果仍需人工复核原始记录和适用标准。'],
    keywords: ['自动计算', '自动判定', '公式', '限值'],
  },
  'project.report-precheck': {
    topicId: 'report', title: '报告预检',
    summary: '在生成报告前检查单位、岗位、检测结果、人员、标准依据和状态是否完整。',
    reportImpact: '阻断项未处理时不能生成受控报告。',
    cautions: ['预检通过只代表字段完整，仍需要复核数据正确性。'],
    keywords: ['报告预检', '阻断项', '缺项'],
  },
  'report.beihai-supplement': {
    topicId: 'report', title: '2026 北海定期检测报告补充内容',
    summary: '按正式模板逐项录入页眉简称、单位概况、物料设备、防护配置、现场采样、结论和建议；未确认内容会阻止生成。',
    reportImpact: '这些内容分别进入报告第3、4、5、6、8、9章及相关附表。',
    cautions: [
      '报告页眉单位简称应由项目人员确认，避免完整单位名称过长影响版式。',
      '未开展或不涉及必须人工确认，系统不会自动删除或代填。',
      '原始记录附表需要采样编号、日期、起止时间、仪器编号和检测条件等逐条数据；资料未齐时必须保留空表并列入待补项。',
    ],
    keywords: ['北海模板', '报告补充', '红字说明', '采样计划'],
  },
  'project.delete': {
    topicId: 'project', title: '删除项目',
    summary: '删除会影响项目及其岗位、结果和报告关联，只能由具备权限的人员执行。',
    cautions: ['删除前确认项目编号。已生成报告的项目不建议直接删除。'],
    keywords: ['删除项目', '批量删除', '影响'],
  },
  'report.preview': {
    topicId: 'report', title: '报告预览',
    summary: '在生成 Word 前查看系统将使用的封面信息、表格、依据和结论。',
    reportImpact: '预览内容应与最终 Word 数据同源。',
    cautions: ['重点核对单位、日期、人员、异常项和结论。'],
    keywords: ['报告预览', '封面', '表格'],
  },
  'report.generate': {
    topicId: 'report', title: '生成 Word 报告',
    summary: '通过预检并满足状态和权限要求后，由系统生成 Word 文件并记录操作人和时间。',
    reportImpact: '生成时固化当前业务数据。',
    keywords: ['生成报告', 'Word', '报告文件'],
  },
  'report.regenerate': {
    topicId: 'report', title: '重新生成报告',
    summary: '数据、人员或依据修改后重新生成当前报告文件。',
    reportImpact: '当前文件会被新版本替换，操作日志继续保留。',
    cautions: ['先完成重新计算、审核和预检，再执行重新生成。'],
    keywords: ['重新生成', '覆盖报告', '修订'],
  },
  'report.download': {
    topicId: 'report', title: '下载和打开报告',
    summary: '使用下载功能将 Word 文件保存到当前电脑。线上容器不能直接打开 Windows 文件夹。',
    cautions: ['最终修改应回到系统数据完成，不建议只修改下载文件。'],
    keywords: ['下载报告', '直接打开', '文件夹'],
  },
  'report.log': {
    topicId: 'report', title: '报告操作日志',
    summary: '记录报告生成、覆盖、下载等关键操作的时间、人员和说明。',
    reportImpact: '用于追溯报告版本和责任操作。',
    keywords: ['报告日志', '生成时间', '操作人'],
  },
  'report.traceability': {
    topicId: 'report', title: '报告数据追溯',
    summary: '通过项目或案例编号关联原始值、计算结果、规则版本、标准依据、审批人员、文件哈希和操作日志。',
    reportImpact: '用于说明报告内容分别来自哪些系统记录，以及生成时采用了哪一版计算与依据。',
    keywords: ['报告追溯', '计算版本', '标准依据', '文件哈希'],
  },
  'radiology.case': {
    topicId: 'radiology-dr', title: 'DR/CT 案例建档',
    summary: '一个案例对应一台设备的一次质量控制和场所防护检测任务。',
    reportImpact: '案例编号、受检机构、设备和检测日期构成报告主索引。',
    keywords: ['案例', '建档', 'DR', 'CT'],
  },
  'radiology.case-steps': {
    topicId: 'radiology-dr', title: 'DR/CT 四步案例建档',
    summary: '依次完成受检单位、设备与模体、检测与人员信息，最后在确认页核对并创建案例。',
    cautions: ['案例编号、受检单位和设备名称为必填项。页面会在进入下一步或创建时定位缺项。'],
    keywords: ['四步建档', '案例信息', '必填项', '确认创建'],
  },
  'radiology.equipment': {
    topicId: 'radiology-dr', title: '放射诊疗设备信息',
    summary: '填写设备名称、厂家、型号、编号和安装场所，确保与现场铭牌一致。',
    reportImpact: '设备信息进入质量控制报告基本信息表。',
    keywords: ['设备厂家', '设备型号', '设备编号'],
  },
  'radiology.instrument': {
    topicId: 'radiology-dr', title: '检测仪器和校准信息',
    summary: '记录实际使用的检测仪器、编号和校准状态，支持检测结果溯源。',
    cautions: ['校准过期或状态异常的仪器不应作为正式检测依据。'],
    keywords: ['检测仪器', '校准', '设备状态'],
  },
  'radiology.excel-import': {
    topicId: 'radiology-dr', title: 'DR/CT Excel 导入',
    summary: '使用系统当前模板导入检测值或防护点位，先执行预检，再正式写入。',
    cautions: ['不要修改工作表名称、字段代码和表头。正式导入后仍需逐项核对。'],
    keywords: ['Excel导入', '预检', '模板'],
  },
  'radiology.attachments': {
    topicId: 'radiology-dr', title: '附件及点位图',
    summary: '包含场所防护检测时需上传点位图。点位图支持PNG、JPEG、WebP；其他附件支持PDF、DOCX、XLSX，单文件不超过25 MB。',
    keywords: ['附件', '点位图'],
  },
  'radiology.phantom': {
    topicId: 'radiology-ct', title: 'CT 检测模体',
    summary: '填写模体名称、厂家、型号、编号以及说明书或技术依据。',
    source: '数据来自模体铭牌、采购资料或厂家说明书。',
    reportImpact: '模体信息进入 CT 检测条件和相关结果说明。',
    cautions: ['不同模体的结构和参考值可能不同，不能只填写通用名称。'],
    keywords: ['CT模体', '厂家', '型号', '说明书'],
  },
  'radiology.ctdi-manufacturer': {
    topicId: 'radiology-ct', title: 'CTDIw 厂家规定值',
    summary: '分别填写头模和体模的厂家规定值，并保留来源说明。不能填写本次实测值。',
    source: '设备随机技术说明书、厂家技术资料或经确认的验收资料。',
    example: '头模厂家规定值 54.2 mGy，体模厂家规定值 22.8 mGy。',
    calculationImpact: '系统使用规定值计算实测值偏差并判定。',
    reportImpact: '规定值、实测值、偏差和结论进入 CT 报告。',
    keywords: ['CTDIw', '厂家规定值', '头模', '体模'],
  },
  'radiology.hcr-baseline': {
    topicId: 'radiology-dr', title: '高对比度分辨力基线',
    summary: '按 lp/mm 录入已确认的分辨力基线。验收检测中厂家规定值、Nyquist 参考值和基线均为空时，以本次读数建立基线；状态检测须填写基线。',
    cautions: ['历史参考值也会参与计算；建立新基线前应核对是否保留了历史参考值。'],
    keywords: ['分辨力', '基线', 'lp/mm'],
  },
  'radiology.lcr-baseline': {
    topicId: 'radiology-dr', title: '低对比度分辨力基线',
    summary: '基线填写可观察影像细节数，单位为“个”。对比度另填在对比度栏，单位为 %。',
    cautions: ['按已确认的客户答复，基线单位仅使用“个”。'],
    keywords: ['低对比度', '基线', '细节数'],
  },
  'radiology.ddi-baseline': {
    topicId: 'radiology-dr', title: 'DDI 基线',
    summary: '录入已确认的探测器剂量指示基线，并与本次 DDI 测量值对应。厂家参考值由人工录入。',
    keywords: ['DDI', '基线'],
  },
  'radiology.project-baseline': {
    topicId: 'radiology-ct', title: '检测项目历史基线',
    summary: '填写当前检测项目经确认的历史基线，核对其单位、来源和检测条件。',
    keywords: ['历史参数', '基线'],
  },
  'radiology.stability-baseline': {
    topicId: 'radiology-ct', title: '稳定性检测基线',
    summary: '分别录入头模和体模的稳定性基线值及确定日期。基线不是当前厂家规定值。',
    source: '设备验收检测或首次稳定性检测确认的记录。',
    calculationImpact: '系统用本次测量结果与基线比较稳定性偏差。',
    cautions: ['基线变更必须有可追溯依据，不应由系统自动猜测。'],
    keywords: ['稳定性基线', '基线日期', '头模', '体模'],
  },
  'radiology.result.TVD': {
    topicId: 'radiology-dr', title: "管电压指示的偏离判定说明",
    summary: "验收、状态检测：±5.0%或±5.0 kV，以较大者控制；稳定性不检测。偏差按预设值小于100 kV显示kV，否则显示%。",
    source: "1014版PPT第10页、客户答复；WS 76—2020",
    keywords: ['项目判定', "TVD"],
  },
  'radiology.result.ROA': {
    topicId: 'radiology-dr', title: "辐射输出量重复性判定说明",
    summary: "验收、状态、稳定性检测：CV≤10.0%。平均值 K̄ 为五次读数的平均值。",
    source: "1014版PPT第11页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ROA"],
  },
  'radiology.result.ROQ': {
    topicId: 'radiology-dr', title: "输出量线性判定说明",
    summary: "验收检测：相邻两档管电流量归一化输出量的相对偏差在±10.0%内；状态、稳定性不检测。",
    source: "1014版PPT第12页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ROQ"],
  },
  'radiology.result.HVL': {
    topicId: 'radiology-dr', title: "有用线束半值层判定说明",
    summary: "验收、状态检测：≥2.3 mmAl（80 kV）；稳定性不检测。平均值为三次读数之和除以3，结果保留1位小数。",
    source: "1014版PPT第13页、客户答复；WS 76—2020",
    keywords: ['项目判定', "HVL"],
  },
  'radiology.result.ETD': {
    topicId: 'radiology-dr', title: "曝光时间指示的偏离判定说明",
    summary: "验收、稳定性检测：t≥100 ms时±10.0%内；t<100 ms时±2 ms或±15.0%，以较大者控制。状态检测不开展。Eᵣ为相对偏差，显示%；绝对偏差（ms）保留在完整计算结果中。",
    source: "1014版PPT第14页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ETD"],
  },
  'radiology.result.ARP': {
    topicId: 'radiology-dr', title: "AEC重复性判定说明",
    summary: "验收、状态检测：CV≤10.0%；稳定性不检测。平均值 K̄ 对应五次读数。",
    source: "1014版PPT第16页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ARP"],
  },
  'radiology.result.ARS': {
    topicId: 'radiology-dr', title: "AEC响应判定说明",
    summary: "验收、状态检测：与平均值比较±20.0%内；稳定性检测±25.0%内。检测结果取相对偏差绝对值最大者。",
    source: "1014版PPT第17页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ARS"],
  },
  'radiology.result.AIC': {
    topicId: 'radiology-dr', title: "AEC电离室之间一致性判定说明",
    summary: "验收检测：±10.0%内；状态检测：±15.0%内；稳定性不开展。相对偏差保留正负号，结果保留1位小数。",
    source: "1014版PPT第18页、客户答复；WS 76—2020",
    keywords: ['项目判定', "AIC"],
  },
  'radiology.result.UBD': {
    topicId: 'radiology-dr', title: "有用线束垂直度偏离判定说明",
    summary: "验收、状态、稳定性检测：≤3.0°。读数可带“>”“<”或不带限定符；偏离度对应录入的角度。",
    source: "1014版PPT第19页、客户答复；WS 76—2020",
    keywords: ['项目判定', "UBD"],
  },
  'radiology.result.LRD': {
    topicId: 'radiology-dr', title: "光野与照射野四边的偏离判定说明",
    summary: "偏离=照射野尺寸−光野尺寸。验收、状态、稳定性检测：±1.0 cm（±10 mm）内。结果保留正负号。",
    source: "1014版PPT第20页、客户答复；WS 76—2020",
    keywords: ['项目判定', "LRD"],
  },
  'radiology.result.DDI': {
    topicId: 'radiology-dr', title: "探测器剂量指示判定说明",
    summary: "验收检测：DDI测量值与人工录入的厂家计算值比较±20.0%，或建立基线。状态检测：与计算值或基线比较±20.0%。稳定性不检测。",
    source: "1014版PPT第22页、客户答复；WS 76—2020",
    keywords: ['项目判定', "DDI"],
  },
  'radiology.result.STP': {
    topicId: 'radiology-dr', title: "信号传递特性判定说明",
    summary: "验收检测R²≥0.98；状态、稳定性检测R²≥0.95。可采用线性、对数或幂函数拟合；幂函数常数项c=0。",
    source: "1014版PPT第23页、客户答复；WS 76—2020",
    keywords: ['项目判定', "STP"],
  },
  'radiology.result.RUE': {
    topicId: 'radiology-dr', title: "响应均匀性判定说明",
    summary: "验收、状态、稳定性检测：CV≤5.0%。拟合模型与STP一致；幂函数按客户答复使用K=(PV/a)^(1/b)。",
    source: "1014版PPT第24页、客户答复；WS 76—2020",
    keywords: ['项目判定', "RUE"],
  },
  'radiology.result.ART': {
    topicId: 'radiology-dr', title: "伪影判定说明",
    summary: "验收、状态、稳定性检测：无影响临床诊断的伪影。存在伪影时记录来源：探测器或监视器。",
    source: "1014版PPT第25页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ART"],
  },
  'radiology.result.REA': {
    topicId: 'radiology-dr', title: "测距误差判定说明",
    summary: "验收、状态检测：±2.0%内；稳定性不开展。结果为相对误差%，标称值和实测值单位为mm。",
    source: "1014版PPT第26页、客户答复；WS 76—2020",
    keywords: ['项目判定', "REA"],
  },
  'radiology.result.ILA': {
    topicId: 'radiology-dr', title: "残影判定说明",
    summary: "验收、稳定性检测：不存在残影，或存在残影而像素值误差≤5.0%；状态检测不开展。D₁为残影区，D₂为非残影区；偏差E与结果说明分别显示。",
    source: "1014版PPT第27页、客户答复；WS 76—2020",
    keywords: ['项目判定', "ILA"],
  },
  'radiology.result.HCR': {
    topicId: 'radiology-dr', title: "高对比度分辨力判定说明",
    summary: "验收检测：≥90.0%厂家规定值，或≥80.0%（fNyquist×1.4），或建立基线。状态检测：与基线比较≥90.0%。稳定性不检测。三个参考值及历史参考值均为空时，验收检测以读数建立基线。",
    source: "1014版PPT第28页、客户答复；WS 76—2020",
    keywords: ['项目判定', "HCR"],
  },
  'radiology.result.LCR': {
    topicId: 'radiology-dr', title: "低对比度分辨力判定说明",
    summary: "验收检测建立基线；状态检测与基线比较不超过2个细节变化；稳定性不检测。按客户答复，基线按“个”记录，%仅用于对比度。",
    source: "1014版PPT第29页、客户答复；WS 76—2020",
    keywords: ['项目判定', "LCR"],
  },
  'radiology.result.TPA': {
    topicId: 'radiology-ct', title: "诊断床定位精度判定说明",
    summary: "分别记录约70 kg负载下连续前进、连续后退的移动示值、起始点和终止点，分别计算定位、归位误差，单位mm。当前系统验收限值为≤2 mm。",
    source: "1014版PPT第59页、客户答复；WS 519—2019",
    keywords: ['项目判定', "TPA"],
  },
  'radiology.result.GTA': {
    topicId: 'radiology-ct', title: "扫描架倾角精度判定说明",
    summary: "按模体检测法或斜率指示器法录入角度及相应长度参数。客户答复明确按WS 519—2019处理，PPT本页误列的管电压公式不采用。当前系统验收限值为≤2°；状态检测不开展。",
    source: "1014版PPT第60页、客户答复；WS 519—2019",
    keywords: ['项目判定', "GTA"],
  },
  'radiology.result.CTDI': {
    topicId: 'radiology-ct', title: "CT剂量指数判定说明",
    summary: "头部：验收与厂家值相差±15%内；状态±20%内，无厂家指标时≤50 mGy；稳定性与基线相差±15%内。体部：验收与厂家值相差±15%内，状态、稳定性不开展。相对厂家偏差单位为%。",
    source: "1014版PPT第61页、客户答复；WS 519—2019",
    keywords: ['项目判定', "CTDI"],
  },
  'radiology.result.LBA': {
    topicId: 'radiology-ct', title: "定位光精度判定说明",
    summary: "结果=测量值×换算系数。验收检测：±2 mm内；状态检测：±3 mm内；稳定性不检测。",
    source: "1014版PPT第63页、客户答复；WS 519—2019",
    keywords: ['项目判定', "LBA"],
  },
  'radiology.result.RST': {
    topicId: 'radiology-ct', title: "重建层厚偏差判定说明",
    summary: "s>2 mm：验收、状态±1 mm内，稳定性与基线相差±20%或±1 mm内，以较大者控制。1≤s≤2 mm：验收±50%内；s<1 mm：验收±0.5 mm内；这两种薄层状态、稳定性不开展。",
    source: "1014版PPT第64页、客户答复；WS 519—2019",
    keywords: ['项目判定', "RST"],
  },
  'radiology.result.MTF': {
    topicId: 'radiology-ct', title: "高对比分辨力判定说明",
    summary: "常规算法、CTDIw<50 mGy条件下，验收检测线对数/MTF10>6.0 lp/cm，状态检测>5.0 lp/cm；稳定性不开展。",
    source: "1014版PPT第65页、客户答复；WS 519—2019",
    keywords: ['项目判定', "MTF"],
  },
  'radiology.result.LCD': {
    topicId: 'radiology-ct', title: "低对比分辨力判定说明",
    summary: "各组计算值=对比度值×最小直径，取三组平均值。验收检测<2.5 mm；状态检测<3.0 mm；稳定性不检测。",
    source: "1014版PPT第66页、客户答复；WS 519—2019",
    keywords: ['项目判定', "LCD"],
  },
  'radiology.result.HUL': {
    topicId: 'radiology-ct', title: "CT值线性判定说明",
    summary: "检测结果取各材料标称值与测量平均值差值的绝对值中的最大值。验收检测±50 HU内；状态、稳定性不检测。",
    source: "1014版PPT第67页、客户答复；WS 519—2019",
    keywords: ['项目判定', "HUL"],
  },
  'radiology.result.HUA': {
    topicId: 'radiology-ct', title: "水的CT值判定说明",
    summary: "验收检测±4 HU内；状态检测±6 HU内；稳定性检测与基线相差±4 HU内。",
    source: "1014版PPT第68页、客户答复；WS 519—2019",
    keywords: ['项目判定', "HUA"],
  },
  'radiology.result.HUU': {
    topicId: 'radiology-ct', title: "水的均匀性判定说明",
    summary: "取四周ROI平均CT值与中心ROI平均CT值的最大差值。验收检测±5 HU内；状态检测±6 HU内；稳定性检测与基线相差±2 HU内。",
    source: "1014版PPT第69页、客户答复；WS 519—2019",
    keywords: ['项目判定', "HUU"],
  },
  'radiology.result.NPS': {
    topicId: 'radiology-ct', title: "噪声判定说明",
    summary: "结果保留2位小数。验收检测<0.35%；状态检测<0.45%；稳定性检测与基线相差±10%内。",
    source: "1014版PPT第70页、客户答复；WS 519—2019",
    keywords: ['项目判定', "NPS"],
  },
  'radiology.raw-value': {
    topicId: 'radiology-dr', title: '性能检测原始值',
    summary: '按检测项目填写现场或仪器原始读数，系统根据字段角色识别原始值和计算值。',
    calculationImpact: '原始值参与均值、偏差、重复性或其他项目公式。',
    cautions: ['核对单位和曝光条件，不能把最终值误填到原始值字段。'],
    keywords: ['性能检测', '原始值', '字段角色'],
  },
  'radiology.calculation': {
    topicId: 'radiology-dr', title: '放射质量控制自动计算',
    summary: '按案例模态、检测项目、输入字段和规则版本生成最终值、限值与判定。',
    source: 'DR 依据 WS 76-2020，CT 依据 WS 519-2019，场所防护依据 GBZ 130-2020。',
    reportImpact: '报告记录当前计算版本和结果快照。',
    cautions: ['计算完成后仍需核对异常、缺项和标准适用性。'],
    keywords: ['自动计算', '规则版本', 'WS 76', 'WS 519'],
  },
  'radiology.protection-point': {
    topicId: 'radiology-dr', title: '场所防护检测点位',
    summary: '记录点位编号、位置描述、多次读数和必要的计算参数。',
    reportImpact: '点位名称、测量值、限值和结论进入场所防护表。',
    cautions: ['点位描述应能让复核人员在现场定位。'],
    keywords: ['防护点位', '位置', '读数'],
  },
  'radiology.annual-workload': {
    topicId: 'radiology-dr', title: '年工作负荷',
    summary: '表示设备预计年度曝光工作量，用于必要情况下估算年有效剂量。',
    source: '受检机构设备使用记录、工作量统计或经确认的运行参数。',
    calculationImpact: '数值越大，估算年有效剂量通常越高。',
    keywords: ['年工作负荷', '年有效剂量', 'mAs'],
  },
  'radiology.occupancy-factor': {
    topicId: 'radiology-dr', title: '居留因子',
    summary: '表示相关人员在防护点邻近区域停留时间的比例。',
    calculationImpact: '参与年有效剂量计算，应按场所实际用途和适用依据确定。',
    cautions: ['不要为了得到合格结果随意降低居留因子。'],
    keywords: ['居留因子', '停留时间', '年剂量'],
  },
  'radiology.use-factor': {
    topicId: 'radiology-dr', title: '使用因子',
    summary: '表示射线束指向某方向的工作比例。',
    calculationImpact: '与年工作负荷、居留因子共同影响年有效剂量。',
    cautions: ['应根据设备运行方式和检测依据确定。'],
    keywords: ['使用因子', '射线方向', '年剂量'],
  },
  'radiology.review': {
    topicId: 'radiology-dr', title: '放射案例审核',
    summary: '审核人员复核原始值、计算结果、标准依据、防护点位和缺项。',
    reportImpact: '审核通过后案例才能进入批准阶段。',
    cautions: ['审核人与录入人员的职责应按机构制度执行。'],
    keywords: ['审核', '待审核', '权限'],
  },
  'radiology.approve': {
    topicId: 'radiology-dr', title: '放射案例批准',
    summary: '有批准权限的人员确认审核结果并使案例具备报告生成条件。',
    reportImpact: '批准人员和时间进入报告快照及操作日志。',
    keywords: ['批准', '审批', '权限'],
  },
  'radiology.report': {
    topicId: 'report', title: 'DR/CT 报告生成',
    summary: '已批准案例可以生成报告。每次生成都会固化案例、测量值、点位、附件哈希和审批信息。',
    cautions: ['数据变化后应重新计算、审核和批准，再生成修订报告。'],
    keywords: ['DR报告', 'CT报告', '快照', '附件哈希'],
  },
  'basic.reference': {
    topicId: 'basic-data', title: '法律法规与标准规范',
    summary: '维护正式名称、文号、级别、日期、有效状态和适用范围。',
    source: '优先使用政府部门、标准发布机构或机构受控资料。',
    reportImpact: '业务方法和报告可引用对应标准编号及版本。',
    keywords: ['法律法规', '标准规范', '文号'],
  },
  'basic.document-upload': {
    topicId: 'basic-data', title: '依据原文上传',
    summary: '上传 PDF、DOC 或 DOCX 原文，填写版本名称和官方来源网址。',
    cautions: ['确认机构有权保存和使用该文件。文件上传不会自动创建计算规则。'],
    keywords: ['原文上传', 'PDF', 'DOCX', '来源网址'],
  },
  'basic.document-version': {
    topicId: 'basic-data', title: '法规标准原文版本',
    summary: '同一法规或标准可保留多个原文版本，并标记当前版本。',
    reportImpact: '历史项目应继续关联当时使用的版本，不能被新文件静默替换。',
    keywords: ['版本记录', '当前版本', '历史引用'],
  },
  'basic.region': {
    topicId: 'basic-data', title: '行政区划',
    summary: '维护北海市及下属区县的标准名称、行政代码、层级和排序。',
    example: '北海市、海城区、银海区、铁山港区、合浦县。',
    keywords: ['地区', '北海市', '行政区划'],
  },
  'basic.device': {
    topicId: 'basic-data', title: '采样与实验设备台账',
    summary: '维护设备名称、类型、型号、编号、状态和校准信息。',
    cautions: ['维修中、报废或校准过期设备不应被正式检测任务选用。'],
    keywords: ['设备台账', '校准', '设备状态'],
  },
  'hazard.category': {
    topicId: 'hazard', title: '危害因素分类',
    summary: '使用中文分类维护化学因素、粉尘、物理因素、生物因素和放射因素。',
    reportImpact: '分类用于业务筛选和报告分组。',
    keywords: ['危害因素分类', '化学因素', '粉尘', '物理因素'],
  },
  'hazard.cas': {
    topicId: 'hazard', title: 'CAS 号',
    summary: '化学物质可填写 CAS 登记号，用于区分同义名称和准确关联 SDS。',
    cautions: ['粉尘和物理因素没有适用 CAS 号时可以留空。'],
    keywords: ['CAS', '化学物质', 'SDS'],
  },
  'hazard.limit': {
    topicId: 'hazard', title: '职业接触限值',
    summary: '按 PC-TWA、PC-STEL、MAC 等类型维护限值、单位和标准文号。',
    calculationImpact: '限值是职业危害检测自动判定的关键输入。',
    cautions: ['必须确认标准版本、指标类型和单位一致。'],
    keywords: ['接触限值', 'PC-TWA', 'PC-STEL', 'MAC'],
  },
  'hazard.sds': {
    topicId: 'hazard', title: 'SDS 安全技术说明书',
    summary: '维护化学品名称、CAS 号、版本和安全技术说明书文件。',
    cautions: ['SDS 应与现场实际产品和供应商版本一致。'],
    keywords: ['SDS', '说明书', '化学品'],
  },
  'config.method': {
    topicId: 'configuration', title: '检测方法',
    summary: '方法名称和代码用于项目、实验结果、计算规则和报告之间的稳定关联。',
    cautions: ['方法代码投入使用后不要随意修改。'],
    keywords: ['检测方法', '方法代码', '简称'],
  },
  'config.formula': {
    topicId: 'configuration', title: '计算公式',
    summary: '记录经确认的公式或公式标识，并明确变量、单位、舍入和边界条件。',
    calculationImpact: '公式修改会影响后续重新计算结果。',
    cautions: ['修改前应通过已知样例验证，并确认生效范围。'],
    keywords: ['计算公式', '变量', '舍入', '边界值'],
  },
  'config.standard-code': {
    topicId: 'configuration', title: '检测依据标准文号',
    summary: '填写方法和公式使用的正式标准编号，多个依据按系统约定分隔。',
    reportImpact: '检测依据可能进入报告评价依据和结果说明。',
    keywords: ['检测依据', '标准文号', 'GBZ'],
  },
  'config.detect-type': {
    topicId: 'configuration', title: '检测分类配置',
    summary: '维护中文分类名称、稳定代码、简称和显示顺序。',
    cautions: ['已有项目引用的代码不应直接删除。'],
    keywords: ['检测分类', '分类代码', '排序'],
  },
  'config.numbering': {
    topicId: 'configuration', title: '机构和编号规则',
    summary: '机构信息用于报告抬头，样品和报告前缀用于生成业务编号。',
    reportImpact: '机构名称、地址、联系方式和资质编号进入报告固定位置。',
    cautions: ['编号规则变更前应确认不会与历史编号重复。'],
    keywords: ['机构配置', '样品编号', '报告编号', '资质'],
  },
  'lab.task': {
    topicId: 'laboratory', title: '内部检测任务单',
    summary: '关联项目创建任务，经签发、领取、实验和提交形成受控流转。',
    cautions: ['项目 ID、任务单号和样品标识必须一致。'],
    keywords: ['任务单', '签发', '领取', '提交'],
  },
  'lab.curve': {
    topicId: 'laboratory', title: '曲线参数',
    summary: '截距、斜率和相关系数用于实验结果计算与曲线质量核对。',
    calculationImpact: '错误参数会直接影响最终浓度或结果值。',
    cautions: ['参数应来自本批次或适用的受控校准曲线。'],
    keywords: ['截距', '斜率', '相关系数', '曲线'],
  },
  'lab.result': {
    topicId: 'laboratory', title: '实验结果计算',
    summary: '根据任务、检测方法、曲线参数、稀释倍数和单位生成最终实验结果。',
    reportImpact: '确认后的结果回到项目检测结果并进入报告。',
    keywords: ['实验结果', '稀释倍数', '结果单位'],
  },
  'system.user-role': {
    topicId: 'system', title: '用户与角色',
    summary: '用户通过角色获得菜单和操作权限，账号停用后不能继续登录。',
    cautions: ['不同人员应使用独立账号，不要共用管理员账号。'],
    keywords: ['用户', '角色', '账号状态'],
  },
  'system.permission': {
    topicId: 'system', title: '菜单与操作权限',
    summary: '菜单权限控制功能可见性，操作权限控制审核、批准、报告生成和规则维护等行为。',
    cautions: ['前端隐藏仅用于体验，实际授权仍由服务端校验。'],
    keywords: ['权限', '菜单权限', '操作权限'],
  },
  'system.password-reset': {
    topicId: 'system', title: '密码重置',
    summary: '管理员可为无法登录的用户重置密码，用户应在安全渠道获取新密码。',
    cautions: ['不要在日志、截图或公共聊天中发送密码。'],
    keywords: ['重置密码', '登录失败', '账号'],
  },
  'system.audit-log': {
    topicId: 'system', title: '操作日志',
    summary: '记录账号、模块、操作、结果和时间，用于问题定位及审计追溯。',
    cautions: ['日志只用于追溯，不替代业务记录和原始记录。'],
    keywords: ['操作日志', '审计', '追溯'],
  },
  'feedback.auto-accept': {
    topicId: 'feedback', title: '提交后自动采纳',
    summary: '意见提交成功后立即进入“已采纳”状态，并写入提交和系统自动采纳两条时间记录。自动采纳表示进入跟踪队列，不表示已经完成开发。',
    keywords: ['自动采纳', '已采纳', '提交意见'],
  },
  'feedback.category': {
    topicId: 'feedback', title: '意见分类',
    summary: '按主要问题选择功能建议、界面体验、操作问题、数据问题、报告问题、性能问题或其他，便于分派和统计。',
    cautions: ['一条意见只选择一个最主要分类；多个独立问题建议分别提交。'],
    keywords: ['分类', '功能建议', '数据问题', '报告问题'],
  },
  'feedback.urgency': {
    topicId: 'feedback', title: '紧急程度',
    summary: '一般表示可按计划处理；紧急表示已明显影响业务，应优先核实。紧急程度不会绕过处理和验证流程。',
    keywords: ['紧急程度', '优先级', '紧急'],
  },
  'feedback.description': {
    topicId: 'feedback', title: '详细说明怎么写',
    summary: '建议依次写当前现象、期望结果、复现步骤和影响范围，让处理人员无需反复询问即可开始核实。',
    example: '在 CT 质量控制选择某案例后，点击“自动计算”无结果；期望显示各检测项计算值；可稳定复现，影响当天报告生成。',
    keywords: ['详细说明', '复现步骤', '期望结果'],
  },
  'feedback.relation': {
    topicId: 'feedback', title: '关联业务编号',
    summary: '填写项目编号、DR/CT 案例编号或报告编号，帮助处理人员定位对应业务记录。',
    cautions: ['只填写系统业务编号，不要填写账号密码。'],
    keywords: ['关联编号', '项目编号', '案例编号', '报告编号'],
  },
  'feedback.attachment': {
    topicId: 'feedback', title: '意见附件',
    summary: '可上传截图、PDF、Word 或 Excel 作为说明，单个文件不超过 10 MB。',
    cautions: ['不要上传密码和无关敏感信息。附件删除动作也会写入时间线。'],
    keywords: ['附件', '截图', 'PDF', 'Excel'],
  },
  'feedback.status': {
    topicId: 'feedback', title: '意见处理状态',
    summary: '状态依次反映已采纳、处理中、待补充、待用户确认和已关闭等阶段。状态文字是判断依据，颜色只作辅助。',
    keywords: ['状态', '已采纳', '处理中', '待确认'],
  },
  'feedback.timeline': {
    topicId: 'feedback', title: '处理时间线',
    summary: '按时间记录提交、自动采纳、处理安排、补充说明、附件和状态变化，包含操作人与发生时间。',
    keywords: ['时间线', '追踪', '处理记录'],
  },
  'feedback.comment': {
    topicId: 'feedback', title: '补充说明',
    summary: '用于补充复现信息、处理进展或沟通结论。保存后会成为时间线记录，不覆盖原始意见。',
    keywords: ['补充说明', '沟通记录', '进展'],
  },
  'feedback.internal-note': {
    topicId: 'feedback', title: '内部处理记录',
    summary: '只对具备意见处理权限的人员显示，用于记录排查过程和内部安排，不向意见提交人展示。',
    cautions: ['最终处理结果应写在公开的处理结果中，不能只留在内部记录。'],
    keywords: ['内部记录', '处理人员', '不可见'],
  },
  'feedback.handling': {
    topicId: 'feedback', title: '处理安排',
    summary: '处理人员可领取意见，并设置优先级、计划完成日期和计划版本；每次修改都会记录到内部时间线。',
    keywords: ['领取处理', '计划完成', '计划版本'],
  },
  'feedback.transition': {
    topicId: 'feedback', title: '下一步操作',
    summary: '系统只显示当前角色和状态允许的操作。要求补充、不予采纳和重新处理时必须填写原因；提交确认时必须填写处理结果。',
    keywords: ['状态流转', '下一步', '处理结果'],
  },
} as const satisfies Record<string, HelpAnchor>

export type HelpAnchorId = keyof typeof helpAnchors

export function findHelpAnchor(id?: string | null): HelpAnchor | undefined {
  if (!id) return undefined
  return helpAnchors[id as HelpAnchorId]
}

export const helpTopicByMenuKey: Record<string, HelpTopicId> = {
  project: 'project',
  'project.workspace': 'project',
  'project.report': 'report',
  radiology: 'radiology-dr',
  'radiology.dr': 'radiology-dr',
  'radiology.ct': 'radiology-ct',
  maintenance: 'maintenance',
  'maintenance.company': 'maintenance',
  'maintenance.position': 'maintenance',
  'maintenance.factor': 'maintenance',
  'maintenance.dict': 'maintenance',
  'maintenance.staff': 'maintenance',
  'maintenance.reportText': 'maintenance',
  basicData: 'basic-data',
  'basicData.law': 'basic-data',
  'basicData.standard': 'basic-data',
  'basicData.region': 'basic-data',
  'basicData.device': 'basic-data',
  hazardFactor: 'hazard',
  'hazardFactor.factor': 'hazard',
  'hazardFactor.sds': 'hazard',
  configCenter: 'configuration',
  'configCenter.detectMethod': 'configuration',
  'configCenter.detectType': 'configuration',
  'configCenter.orgConfig': 'configuration',
  labManage: 'laboratory',
  'labManage.task': 'laboratory',
  'labManage.result': 'laboratory',
  feedback: 'feedback',
  system: 'system',
  'system.user': 'system',
  'system.role': 'system',
  'system.menu': 'system',
  'system.permission': 'system',
  'system.dept': 'system',
  'system.post': 'system',
  'system.notice': 'system',
  'system.syslog': 'system',
  'system.sysfile': 'system',
  'system.sysconfig': 'system',
  help: 'overview',
}

export function findHelpTopic(id: HelpTopicId): HelpTopic {
  return helpTopics.find((topic) => topic.id === id) || helpTopics[0]
}
