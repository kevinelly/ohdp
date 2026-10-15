// Compare draft objects independently of key insertion order.
export function sameDraft(a:unknown,b:unknown):boolean {
  const canonical=(value:any):any=>Array.isArray(value)?value.map(canonical):value && typeof value==='object'?Object.fromEntries(Object.keys(value).sort().filter(k=>value[k]!==undefined).map(k=>[k,canonical(value[k])])):value
  return JSON.stringify(canonical(a))===JSON.stringify(canonical(b))
}

// Only replace the numeric token for this unit; preserve other historical text verbatim.
function parameterPattern(unit:'kV'|'mA') { return new RegExp('([0-9]+(?:\\.[0-9]+)?(?:\\s*[-~～至]\\s*[0-9]+(?:\\.[0-9]+)?)?)\\s*'+unit+'(?![a-zA-Z])','i') }
export function equipmentParameter(text:string,unit:'kV'|'mA'):string {return text.match(parameterPattern(unit))?.[1] || ''}
export function updateEquipmentParameter(text:string,unit:'kV'|'mA',value:string):string {
 const pattern=parameterPattern(unit),match=pattern.exec(text)
 if(match) return text.slice(0,match.index)+(value.trim()?value.trim()+' '+unit:'')+text.slice(match.index+match[0].length)
 return value.trim()?text+(text.trim()?'；':'')+value.trim()+' '+unit:text
}
