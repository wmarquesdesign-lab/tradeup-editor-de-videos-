const Effects = {
  presets:{
    normal:{brightness:100,contrast:100,saturation:100,warmth:0,blur:0,sharpen:0},
    venda:{brightness:112,contrast:118,saturation:128,warmth:8,blur:0,sharpen:8},
    cinema:{brightness:95,contrast:122,saturation:92,warmth:-4,blur:0,sharpen:4},
    comida:{brightness:108,contrast:112,saturation:145,warmth:12,blur:0,sharpen:6},
    noite:{brightness:88,contrast:135,saturation:105,warmth:-10,blur:0,sharpen:10}
  },
  setPreset(name){
    State.settings.effects=Object.assign(State.settings.effects||{}, Effects.presets[name]||Effects.presets.normal, {preset:name});
    Effects.syncUI(); Effects.apply(); Timeline.render(); Project.autosave();
    UI.status('Filtro aplicado: '+name);
  },
  update(){
    const e=State.settings.effects;
    ['brightness','contrast','saturation','warmth','blur','sharpen'].forEach(k=>{
      const el=document.getElementById('fx_'+k); if(el)e[k]=Number(el.value||0);
    });
    Effects.apply(); Project.autosave();
  },
  cssFilter(){
    const e=State.settings.effects||{};
    return `brightness(${e.brightness||100}%) contrast(${e.contrast||100}%) saturate(${e.saturation||100}%) blur(${e.blur||0}px)`;
  },
  apply(){
    const v=document.getElementById('previewVideo'); if(v)v.style.filter=Effects.cssFilter();
    const stage=document.getElementById('previewStage'); if(stage)stage.style.setProperty('--warmth', ((State.settings.effects?.warmth||0)/100));
  },
  applyCanvasFilter(ctx){ ctx.filter=Effects.cssFilter(); },
  syncUI(){
    const e=State.settings.effects||{};
    ['brightness','contrast','saturation','warmth','blur','sharpen'].forEach(k=>{
      const el=document.getElementById('fx_'+k); if(el)el.value=e[k]??(k==='brightness'||k==='contrast'||k==='saturation'?100:0);
    });
    Effects.apply();
  }
};
