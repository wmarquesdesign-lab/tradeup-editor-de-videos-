const State = {
  version:'tradeup-final-ai-desktop',
  videos:[], audio:null, images:[], texts:[], selectedClipId:null, selectedTextId:null,
  playIndex:0, playheadMs:0, zoom:1,
  settings:{
    fileName:'meu_video_tradeup.mp4', resolution:'1080p', fps:30,
    musicVolume:0.7, fadeIn:0, fadeOut:0,
    format:'vertical', aspect:'9:16',
    effects:{brightness:100,contrast:100,saturation:100,warmth:0,blur:0,sharpen:0,preset:'normal'},
    ai:{lastSuggestion:'', autoApplied:false}
  }
};
const Project = {
  newProject(){
    if(confirm('Criar um projeto novo?')){
      State.videos=[];State.audio=null;State.images=[];State.texts=[];
      State.selectedClipId=null;State.playIndex=0;State.playheadMs=0;
      Project.autosave(); Timeline.render(); Media.render(); Player.loadClip(0);
      UI.status('Projeto novo criado.');
    }
  },
  serialize(){
    return {
      version:State.version, createdAt:new Date().toISOString(),
      videos:State.videos, audio:State.audio, images:State.images, texts:State.texts,
      settings:State.settings, renderPlan:Exporter.renderPlan(),
      aiPlan: (window.AIStudio ? AIStudio.plan() : null)
    };
  },
  save(){ Native.saveProjectFile(Project.serialize()); Project.autosave(); },
  package(){ Native.exportProjectPackage(Project.serialize()); },
  open(){ Native.openProjectFile(); },
  downloadJson(json){
    const a=document.createElement('a');
    a.href=URL.createObjectURL(new Blob([JSON.stringify(json,null,2)],{type:'application/json'}));
    a.download='projeto.tradeup.json'; a.click();
  },
  autosave(){
    try{ localStorage.setItem('tradeup.autosave', JSON.stringify(Project.serialize())); }catch(e){}
  },
  restoreAutosave(){
    try{
      const raw=localStorage.getItem('tradeup.autosave');
      if(raw && confirm('Existe um projeto salvo automaticamente. Deseja restaurar?')) Project.load(JSON.parse(raw));
    }catch(e){}
  },
  load(json){
    try{
      const data= typeof json==='string'?JSON.parse(json):json;
      State.videos=data.videos||[]; State.audio=data.audio||null; State.images=data.images||[]; State.texts=data.texts||[];
      State.settings=Object.assign(State.settings,data.settings||{});
      State.settings.effects=Object.assign({brightness:100,contrast:100,saturation:100,warmth:0,blur:0,sharpen:0,preset:'normal'}, State.settings.effects||{});
      State.selectedClipId=State.videos[0]?.id??null; State.playIndex=0; State.playheadMs=0;
      Timeline.render(); Media.render(); Player.loadClip(0); TextTools.render(); Effects.syncUI();
      UI.status('Projeto importado com sucesso.');
    }catch(e){ UI.status('Erro ao importar projeto: '+e.message); }
  }
};
function loadProjectFromNative(json){ Project.load(json); }
