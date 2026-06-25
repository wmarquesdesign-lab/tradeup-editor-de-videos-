const AIStudio = {
  plan(){
    const total=Timeline.totalMs();
    return {
      totalMs:total,
      clips:State.videos.length,
      suggestedFormat: total<45000?'stories/reels':'horizontal',
      warnings:AIStudio.validate(),
      storyboard:State.videos.map((v,i)=>({clip:i+1,name:v.name,durationMs:Timeline.clipDurationMs(v),transition:v.transition||'none'}))
    };
  },
  validate(){
    const warnings=[];
    if(!State.videos.length) warnings.push('Nenhum vídeo carregado.');
    State.videos.forEach((v,i)=>{ if(Timeline.clipDurationMs(v)>15000) warnings.push('Clipe '+(i+1)+' está longo; corte automático recomendado.'); });
    if(!State.audio) warnings.push('Sem música de fundo.');
    if(!State.texts.length) warnings.push('Sem títulos/legendas.');
    return warnings;
  },
  autoEdit(){
    if(!State.videos.length) return UI.status('Adicione vídeos antes de usar IA.');
    State.videos.forEach((v,i)=>{
      const max=i===0?8000:6500;
      v.trimIn=v.trimIn||0;
      v.trimOut=Math.min(v.durationMs||max, (v.trimIn||0)+max);
      v.speed=1;
      v.transition=i<State.videos.length-1?(['fade','dissolve','slide','zoom'][i%4]):'none';
      v.transitionMs=700;
    });
    if(!State.texts.length){
      State.texts.push({id:Date.now(),text:'Oferta especial',startMs:300,durationMs:3000,color:'#ffffff',size:44});
      State.texts.push({id:Date.now()+1,text:'Rio Sul Miguel Pereira',startMs:Math.max(0,Timeline.totalMs()-4000),durationMs:3500,color:'#ffd34d',size:38});
    }
    State.settings.fadeIn=.8; State.settings.fadeOut=1.2;
    State.settings.format='vertical';
    Effects.setPreset('venda');
    Timeline.render(); Media.render(); TextTools.render(); Player.loadClip(0); AIStudio.report('IA aplicou cortes curtos, transições, filtro de venda e títulos automáticos.');
  },
  makeCaptions(){
    if(!State.videos.length) return UI.status('Adicione vídeos primeiro.');
    const base=document.getElementById('aiPrompt')?.value.trim() || 'Confira essa novidade no Rio Sul';
    State.texts=[];
    let cursor=0;
    State.videos.forEach((v,i)=>{
      const dur=Math.min(3500,Timeline.clipDurationMs(v));
      State.texts.push({id:Date.now()+i,text:i===0?base:'Mais ofertas esperando por você',startMs:cursor+300,durationMs:dur,color:i%2?'#ffd34d':'#ffffff',size:i===0?42:34});
      cursor+=Timeline.clipDurationMs(v);
    });
    TextTools.render(); Timeline.render(); AIStudio.report('Legendas automáticas criadas na faixa de texto.');
  },
  suggest(){
    const warnings=AIStudio.validate();
    const total=UI.fmt(Timeline.totalMs());
    const msg=[
      'Análise IA local:',
      '• '+State.videos.length+' clipe(s), duração '+total,
      '• Formato recomendado: '+(Timeline.totalMs()<60000?'1080x1920 Reels/Stories':'1920x1080 horizontal'),
      warnings.length?'• Ajustes: '+warnings.join(' | '):'• Projeto pronto para exportar.'
    ].join('\n');
    AIStudio.report(msg);
  },
  report(msg){
    State.settings.ai.lastSuggestion=msg;
    const el=document.getElementById('aiOutput'); if(el) el.textContent=msg;
    UI.status(msg.split('\n')[0]);
    Project.autosave();
  }
};
