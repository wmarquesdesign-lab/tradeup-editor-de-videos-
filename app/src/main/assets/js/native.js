const Native = {
  isAndroid(){ return !!window.AndroidBridge; },
  pickVideos(){
    if(window.AndroidBridge) return AndroidBridge.pickVideos();
    return Native.pickFiles('video/*', true, async files=>{
      const items=[];
      for(const file of files){
        const url=URL.createObjectURL(file);
        const durationMs=await Native.readDuration(url);
        items.push({id:Date.now()+Math.random(), name:file.name, uri:url, previewUrl:url, durationMs, file});
      }
      Media.addVideos(items);
      UI.status(files.length+' vídeo(s) adicionados no modo PC.');
    });
  },
  pickImages(){
    if(window.AndroidBridge) return AndroidBridge.pickImages();
    return Native.pickFiles('image/*', true, files=>{
      const items=[...files].map((file,i)=>({id:Date.now()+i, name:file.name, uri:URL.createObjectURL(file), previewUrl:URL.createObjectURL(file), file}));
      Media.addImages(items);
    });
  },
  pickAudio(){
    if(window.AndroidBridge) return AndroidBridge.pickAudio();
    return Native.pickFiles('audio/*', false, async files=>{
      const file=files[0]; if(!file)return;
      const url=URL.createObjectURL(file);
      const durationMs=await Native.readAudioDuration(url);
      setAudioTrack({name:file.name, uri:url, previewUrl:url, durationMs, file});
      UI.status('Música adicionada no modo PC.');
    });
  },
  openProjectFile(){
    if(window.AndroidBridge) return AndroidBridge.openProjectFile();
    return Native.pickFiles('.json,application/json', false, files=>{
      const f=files[0]; if(!f)return;
      const reader=new FileReader();
      reader.onload=()=>Project.load(reader.result);
      reader.readAsText(f);
    });
  },
  exportVideo(json){
    if(window.AndroidBridge) return AndroidBridge.exportVideo(JSON.stringify(json));
    return Exporter.exportDesktop();
  },
  saveProjectFile(json){
    if(window.AndroidBridge) return AndroidBridge.saveProjectFile(JSON.stringify(json));
    Project.downloadJson(json);
  },
  exportProjectPackage(json){
    if(window.AndroidBridge) return AndroidBridge.exportProjectPackage(JSON.stringify(json));
    const blob=new Blob([JSON.stringify(json,null,2)],{type:'application/json'});
    Native.downloadBlob(blob,'pacote_tradeup_projeto.tradeup.json');
    UI.status('No PC foi salvo o projeto JSON. Para ZIP completo, use o Android ou compacte a pasta manualmente.');
  },
  pickFiles(accept, multiple, cb){
    const input=document.createElement('input');
    input.type='file'; input.accept=accept||'*/*'; input.multiple=!!multiple;
    input.style.display='none';
    document.body.appendChild(input);
    input.onchange=()=>{ const files=[...input.files]; document.body.removeChild(input); cb(files); };
    input.click();
  },
  readDuration(url){
    return new Promise(resolve=>{
      const v=document.createElement('video'); v.preload='metadata'; v.src=url;
      v.onloadedmetadata=()=>resolve(Math.round((v.duration||5)*1000));
      v.onerror=()=>resolve(5000);
    });
  },
  readAudioDuration(url){
    return new Promise(resolve=>{
      const a=document.createElement('audio'); a.preload='metadata'; a.src=url;
      a.onloadedmetadata=()=>resolve(Math.round((a.duration||0)*1000));
      a.onerror=()=>resolve(0);
    });
  },
  downloadBlob(blob, filename){
    const a=document.createElement('a');
    a.href=URL.createObjectURL(blob); a.download=filename; document.body.appendChild(a); a.click();
    setTimeout(()=>{URL.revokeObjectURL(a.href); a.remove();},1000);
  },
  toast(msg){ if(window.AndroidBridge) AndroidBridge.toast(String(msg)); else console.log(msg); }
};
