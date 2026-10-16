// Historical seed data was imported with a wrong connection encoding. Repair only
// known damaged display strings; identifiers and all authorization data stay intact.
const roles:Record<string,[string,string]>={
 RADIOLOGY_REVIEWER:['放射检测审核员','复核 DR/CT 原始值、计算结果与防护点位'],
 RADIOLOGY_APPROVER:['放射检测批准人','批准已审核的 DR/CT 检测案例'],
 RADIOLOGY_REPORTER:['放射检测报告员','生成、归档并修订 DR/CT 检测报告'],
 RADIOLOGY_RULE_ADMIN:['放射规则管理员','维护 DR/CT 版本化判定规则']
}
const permissions:Record<string,string>={
 'RADIOLOGY:WORKFLOW:REVIEW':'放射检测审核按钮','RADIOLOGY:WORKFLOW:REVIEW:API':'放射检测审核接口',
 'RADIOLOGY:WORKFLOW:APPROVE':'放射检测批准按钮','RADIOLOGY:WORKFLOW:APPROVE:API':'放射检测批准接口',
 'RADIOLOGY:REPORT:GENERATE':'放射检测报告按钮','RADIOLOGY:REPORT:GENERATE:API':'放射检测报告生成接口',
 'RADIOLOGY:REVISION:CREATE:API':'放射检测修订接口','RADIOLOGY:RULE:MANAGE':'放射判定规则按钮',
 'RADIOLOGY:RULE:CREATE:API':'放射判定规则新增接口','RADIOLOGY:RULE:UPDATE:API':'放射判定规则修改接口','RADIOLOGY:RULE:DELETE:API':'放射判定规则删除接口'
}
function repair(text:string|undefined,correct:string|undefined){return correct&&text&&/[æåçèéäÃÂ]/.test(text)?correct:text}
export function roleDisplay<T extends {roleCode:string;roleName:string;remark?:string}>(row:T):T {
 const known=roles[row.roleCode];return {...row,roleName:repair(row.roleName,known?.[0])!,remark:repair(row.remark,known?.[1])}
}
export function permissionDisplay<T extends {permCode:string;permName:string}>(row:T):T{return {...row,permName:repair(row.permName,permissions[row.permCode])!}}
