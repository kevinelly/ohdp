// Compare draft objects independently of key insertion order.
export function sameDraft(a:unknown,b:unknown):boolean {
  const canonical=(value:any):any=>Array.isArray(value)?value.map(canonical):value && typeof value==='object'?Object.fromEntries(Object.keys(value).sort().filter(k=>value[k]!==undefined).map(k=>[k,canonical(value[k])])):value
  return JSON.stringify(canonical(a))===JSON.stringify(canonical(b))
}
