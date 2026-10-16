export interface ItemScope {itemCode:string;group:string;selected:boolean;applicable:boolean;pending:boolean;reason:string;modelDependent?:boolean}
export interface ScopeProfile {selectedGroups?:string[];scopeConfirmed?:boolean|null;itemApplicability?:Record<string,boolean|null>;itemExclusionReasons?:Record<string,string>}
export function scopeSelection(profile:ScopeProfile){return {selectedGroups:profile.selectedGroups,scopeConfirmed:profile.scopeConfirmed,itemApplicability:profile.itemApplicability,itemExclusionReasons:profile.itemExclusionReasons}}
export function applicableQuality(scopes:ItemScope[]){return scopes.filter(s=>s.selected&&s.applicable&&!s.pending&&s.itemCode!=='RPD')}
export function adjacentQuality(scopes:ItemScope[],current:string,direction:1|-1){const items=applicableQuality(scopes);const index=items.findIndex(s=>s.itemCode===current);return items[index+direction]?.itemCode}
export function qualityStatus(scope:ItemScope,saved:boolean,fresh:boolean,complete:boolean){return !scope.selected?'未选择':scope.pending?'待确认':!scope.applicable?'不开展':fresh&&complete?'已计算':saved?'已保存':'待录入'}

// UI progression follows the PPT sequence; report ordering remains server-owned.
export function pptOrderedScopes(scopes:ItemScope[]){
 const codes=['TVD','ROA','ROQ','HVL','ETD','ARP','ARS','AIC','UBD','LRD','DDI','STP','RUE','ART','REA','ILA','HCR','LCR','TPA','GTA','CTDI','LBA','RST','MTF','LCD','HUL','HUA','HUU','NPS','RPD']
 const rank=(code:string)=>{const n=codes.indexOf(code);return n<0?codes.length:n}
 return [...scopes].sort((a,b)=>rank(a.itemCode)-rank(b.itemCode))
}
