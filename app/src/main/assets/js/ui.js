const UI = {
  openPanel(name){
    document.querySelectorAll('.panel').forEach(p=>p.classList.remove('active'));
    document.querySelectorAll('.tool-tabs button,.bottom-tabs button').forEach(b=>b.classList.remove('active'));
    const p=document.getElementById('panel-'+name); if(p)p.classList.add('active');
    document.querySelectorAll(`[data-panel="${name}"]`).forEach(b=>b.classList.add('active'));
  },
  status(msg){ const s=document.getElementById('status'); if(s)s.textContent=msg; if(window.AndroidBridge) Native.toast(msg); },
  toast(msg){ UI.status(msg); },
  escape(s){ return String(s||'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); },
  fmt(ms){ ms=Math.max(0,Number(ms||0)); const s=Math.floor(ms/1000), m=Math.floor(s/60), r=s%60; return String(m).padStart(2,'0')+':'+String(r).padStart(2,'0'); }
};
function setStatus(msg){ UI.status(msg); }
