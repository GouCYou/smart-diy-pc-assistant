import {computed,ref} from 'vue';
export type ThemeMode='system'|'light'|'dark';
const system=window.matchMedia('(prefers-color-scheme: dark)');
let saved:ThemeMode='system';
try {const value=localStorage.getItem('smartdiy-theme');if(value==='light'||value==='dark')saved=value;}catch{}
export const themeMode=ref<ThemeMode>(saved),systemDark=ref(system.matches);
export const darkTheme=computed(()=>themeMode.value==='dark'||(themeMode.value==='system'&&systemDark.value));
function apply(){document.documentElement.classList.toggle('dark',darkTheme.value);document.documentElement.dataset.theme=darkTheme.value?'dark':'light';document.querySelector('meta[name="theme-color"]')?.setAttribute('content',darkTheme.value?'#111720':'#f7f8fa');}
export function setTheme(mode:ThemeMode){themeMode.value=mode;try{localStorage.setItem('smartdiy-theme',mode);}catch{}apply();}
system.addEventListener('change',event=>{systemDark.value=event.matches;apply();});
apply();
