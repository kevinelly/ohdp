// Compare draft objects independently of key insertion order.
export function sameDraft(a:unknown,b:unknown):boolean {
  const canonical=(value:any):any=>Array.isArray(value)?value.map(canonical):value && typeof value==='object'?Object.fromEntries(Object.keys(value).sort().filter(k=>value[k]!==undefined).map(k=>[k,canonical(value[k])])):value
  return JSON.stringify(canonical(a))===JSON.stringify(canonical(b))
}

// The API retains one canonical case unit. Per-reading controls convert at the
// boundary, so selecting another display unit never reinterprets stored values.
export function convertDoseRate(value:string|number|undefined|null,from:string,to:string):string {
 if(value===undefined || value===null || value==='')return ''
 const scales:Record<string,number>={'nSv/h':.001,'μSv/h':1,'mSv/h':1000}
 const number=Number(value)
 if(!Number.isFinite(number) || !scales[from] || !scales[to])return String(value)
 return String(Number((number*scales[from]!/scales[to]!).toPrecision(15)))
}

// Only replace the numeric token for this unit; preserve other historical text verbatim.
function parameterPattern(unit:'kV'|'mA') { return new RegExp('([0-9]+(?:\\.[0-9]+)?(?:\\s*[-~～至]\\s*[0-9]+(?:\\.[0-9]+)?)?)\\s*'+unit+'(?![a-zA-Z])','i') }
export function equipmentParameter(text:string,unit:'kV'|'mA'):string {return text.match(parameterPattern(unit))?.[1] || ''}
export function updateEquipmentParameter(text:string,unit:'kV'|'mA',value:string):string {
 const pattern=parameterPattern(unit),match=pattern.exec(text)
 if(match) return text.slice(0,match.index)+(value.trim()?value.trim()+' '+unit:'')+text.slice(match.index+match[0].length)
 return value.trim()?text+(text.trim()?'；':'')+value.trim()+' '+unit:text
}

export function pptItemName(code:string,name:string){return ({AIC:'AEC电离室之间一致性',DDI:'探测器剂量指示（DDI）',STP:'信号传递特性（STP）',CTDI:'CT剂量指数（CTDIW）',LCD:'低对比分辨力',HUA:'水的CT值',HUU:'水的均匀性',RPD:'场所防护本底与校准'} as Record<string,string>)[code] || name}

// PPT pages 11, 12, 16, 30 and 71 name these means K-bar. Keep other
// quantities and persisted result identifiers unchanged, including repeated rows.
export function pptResultName(item:string,code:string,label:string):string {
 const resultCode=code.replace(/^R\d+__/, '')
 if(['ROA','ROQ','ARP','RPD'].includes(item) && /^MEAN(?:_[12])?$/.test(resultCode) && !label.includes('K̄'))return label+' K̄'
 if(item==='ETD' && /^MEAN_[123]$/.test(resultCode))return '平均值 T̄'
 if(item==='ETD' && /^DEVIATION_PERCENT_[123]$/.test(resultCode))return '检测结果 Eᵣ'
 return label
}

// Display labels only: persisted field codes remain compatible with existing records.
export function pptFieldName(item:string,code:string,fallback:string):string {
 const names:Record<string,string>={UBD:'读数','UBD-QUALIFIER':'读数限定符','ART-SOURCE':'伪影来源','填“是”或“否”':'是否存在伪影',
  '是否有残影（如“否”无需填后两项）':'是否存在残影','残影区D1':'残影区ROI值D₁','非残影区D0':'非残影区ROI值D₂',
  LBA:'测量值','LBA-FACTOR':'换算系数',MTF:'读数','MTF-FACTOR':'换算系数','RST-FACTOR':'层厚换算系数',
  'NPS-n':'层厚n',R0:'标称值','ROA-K':'校准因子','ROQ-K':'校准因子','LCR1（可观察细节数）':'可观察影像细节数','LCR2（可观察对比度）':'对比度','厂家规定值/基线值':item==='HCR'?'厂家规定值/基线值':'基线值',
  'HCR-MANUFACTURER':'厂家规定值','HCR-NYQUIST':'fNyquist','HCR-BASELINE':'基线值',HCR1:'读数','LCR-BASELINE-UNIT':'基线单位','STP-MODEL':'拟合关系',k:'仪器校准因子k₁',a:'单位换算常数a','RPD-k2':'时间修正因子k₂',
  'CTDI-HEAD-MANUFACTURER':'厂家CT剂量指数CTDIW','CTDI-BODY-MANUFACTURER':'厂家CT剂量指数CTDIW'}
 if(names[code])return names[code]!
 if(item==='GTA')return fallback.replace(/([αL])([12])/g,(_,letter,digit)=>letter+'₀₁₂₃₄₅₆₇₈₉'[Number(digit)])
 if(/^D(?:for|back)$/.test(code))return '诊断床移动示值D'
 if(/^L(?:for|back)$/.test(code))return '起始点L'
 if(/^C(?:for|back)$/.test(code))return '终止点C'
 if(/^STP-K\d$/.test(code))return '入射空气比释动能K'
 if(/^STP-R\d$/.test(code))return '平均像素值PV'
 if(/^RUE\d$/.test(code))return '像素值PV'
 if(/^REA-[ab][tm]$/.test(code))return code.endsWith('t')?'标称值Dt':'实测值Dm'
 if(/^LRD-[ab][12]-[01]$/.test(code))return code.endsWith('0')?'光野':'照射野'
 if(/^(?:ARS[12]-\d|AIC-[CLR]\d|[HS][CUDLR]D\d|HUU-[UDLR]\d)$/.test(code))return '读数'+code.slice(-1)
 if(/^(?:Air|LDPE|Acylic|Teflon)-HU\d$/.test(code))return '读数'+code.slice(-1)
 if(item==='RST' && /^R[1-4]$/.test(code))return '实测值'+code.slice(-1)
 if(/^(?:ROA|ARP)[1-5]$/.test(code))return '读数K'+'₀₁₂₃₄₅₆₇₈₉'[Number(code.slice(-1))]
 if(/^ROQ[12]-[1-5]$/.test(code))return '读数K'+'₀₁₂₃₄₅₆₇₈₉'[Number(code.slice(-1))]
 if(/^(?:HVL|HUA|NPS|DDI)[1-3]$/.test(code))return '读数'+code.slice(-1)
 if(/^RPD0-\d+$/.test(code))return '本底读数'+code.split('-')[1]
 if(/^I[12]$/.test(code))return '管电流I'
 if(/^T[12]$/.test(code))return '曝光时间t'
 if(/^(?:TVD|ETD)\d-0$/.test(code))return item==='TVD'?'预设值':'指示值T₀'
 if(/^(?:TVD|ETD)\d-[123]$/.test(code))return (item==='ETD'?'读数T':'读数')+code.slice(-1)
 if(/^(?:TVD|ETD)-K\d$/.test(code))return item==='ETD'?'校准因子 K₃':'校准因子'
 return fallback
}
export function attachmentCategoryName(code:string):string{return ({POINT_MAP:'检测点位图',CERTIFICATE:'仪器证书',PHANTOM_MANUAL:'模体说明书',OTHER:'其他附件'} as Record<string,string>)[code]||code}
export function ruleLimitText(rule:{lowerLimit?:number|null;upperLimit?:number|null;lowerInclusive?:boolean;upperInclusive?:boolean;expectedText?:string|null;unit?:string}):string {
 return [rule.lowerLimit!=null?(rule.lowerInclusive===false?'>':'≥')+rule.lowerLimit:'',rule.upperLimit!=null?(rule.upperInclusive===false?'<':'≤')+rule.upperLimit:'',rule.expectedText?'应为“'+rule.expectedText+'”':'',rule.unit||''].filter(Boolean).join(' ')||'未设置限值'
}
