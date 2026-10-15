export function doseDraftKey(username:string,id?:number){return `personal-dose-draft:v2:${username}:${id || 'new'}`}

export async function allLibraryPages<T>(fetchPage:(page:number)=>Promise<{content:T[];last?:boolean;totalPages?:number}>):Promise<T[]> {
  const rows:T[]=[]
  for(let page=0;;page++){
    const result=await fetchPage(page)
    rows.push(...result.content)
    if(result.last===true || (result.totalPages!==undefined && page+1>=result.totalPages) || (result.totalPages===undefined && result.content.length<100))return rows
    if(result.content.length===0)throw new Error('资料库分页返回异常，请刷新重试')
  }
}
