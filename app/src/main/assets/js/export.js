const Exporter = {
  renderPlan(){
    let cursor=0;
    const plan=State.videos.map((v,i)=>{
      const item={
        index:i,name:v.name,source:v.uri,previewUrl:v.previewUrl,
        trimInMs:v.trimIn||0,trimOutMs:v.trimOut||v.durationMs||0,
        durationMs:Timeline.clipDurationMs(v),timelineStartMs:cursor,
        transition:v.transition||'none',transitionMs:Number(v.transitionMs||700),
        speed:v.speed||1,effects:State.settings.effects
      };
      cursor+=item.durationMs; return item;
    });
    return plan;
  },
  exportVideo(){
    State.settings.fileName=document.getElementById('exportName').value||'meu_video_tradeup.mp4';
    State.settings.resolution=document.getElementById('exportRes').value;
    State.settings.fps=Number(document.getElementById('exportFps').value);
    const fmt=document.getElementById('exportFormat'); if(fmt) State.settings.format=fmt.value;
    Project.autosave();
    const payload=Project.serialize(); payload.fileName=State.settings.fileName;
    Native.exportVideo(payload);
  },
  async exportDesktop(){
    if(!State.videos.length) return UI.status('Escolha pelo menos um vídeo.');
    const fps=Number(State.settings.fps||30);
    const {w,h}=Exporter.sizeFor(State.settings.resolution, State.settings.format);
    const canvas=document.createElement('canvas'); canvas.width=w; canvas.height=h;
    const ctx=canvas.getContext('2d');
    const stream=canvas.captureStream(fps);
    const chunks=[];
    const mime=MediaRecorder.isTypeSupported('video/webm;codecs=vp9')?'video/webm;codecs=vp9':'video/webm';
    const rec=new MediaRecorder(stream,{mimeType:mime, videoBitsPerSecond:8000000});
    rec.ondataavailable=e=>{ if(e.data && e.data.size) chunks.push(e.data); };
    const done=new Promise(resolve=>rec.onstop=resolve);
    rec.start(500);
    Exporter.progress(3,'Renderizando no PC em WebM...');
    const total=Math.max(1,Timeline.totalMs()); let rendered=0;
    const bg='#05070d';
    for(let i=0;i<State.videos.length;i++){
      const clip=State.videos[i];
      const video=document.createElement('video');
      video.src=clip.previewUrl||clip.uri; video.muted=true; video.playsInline=true; video.preload='auto';
      await Exporter.waitMeta(video);
      const start=(clip.trimIn||0)/1000;
      const end=(clip.trimOut||clip.durationMs||video.duration*1000)/1000;
      const duration=Math.max(0.5,(end-start)/(clip.speed||1));
      for(let t=0;t<duration;t+=1/fps){
        video.currentTime=Math.min(end,start+t*(clip.speed||1));
        await Exporter.waitSeek(video);
        ctx.fillStyle=bg; ctx.fillRect(0,0,w,h);
        Exporter.drawFit(ctx,video,w,h);
        Exporter.drawOverlays(ctx,(rendered+t*1000),w,h);
        Exporter.drawHud(ctx,clip,i,w,h);
        await Exporter.sleep(1000/fps);
      }
      rendered+=duration*1000;
      Exporter.progress(Math.min(96,Math.round(rendered*96/total)),'Renderizando clipe '+(i+1)+'/'+State.videos.length);
    }
    rec.stop(); await done;
    const blob=new Blob(chunks,{type:mime});
    const name=(State.settings.fileName||'tradeup_video').replace(/\.mp4$/i,'')+'_PC.webm';
    Native.downloadBlob(blob,name);
    Exporter.progress(100,'Exportação PC concluída: '+name);
  },
  waitMeta(video){ return new Promise(res=>{ if(video.readyState>=1)res(); else {video.onloadedmetadata=res; video.onerror=res;} }); },
  waitSeek(video){ return new Promise(res=>{ if(video.readyState>=2) setTimeout(res,8); else video.oncanplay=()=>res(); video.onseeked=()=>res(); setTimeout(res,150); }); },
  sleep(ms){ return new Promise(r=>setTimeout(r,ms)); },
  sizeFor(res,fmt){
    const vertical=fmt==='vertical'||fmt==='stories'||fmt==='9:16';
    const square=fmt==='square'||fmt==='1:1';
    let base=res==='4K'?2160:res==='720p'?720:1080;
    if(square) return {w:base,h:base};
    return vertical?{w:Math.round(base*9/16),h:base}:{w:Math.round(base*16/9),h:base};
  },
  drawFit(ctx,img,w,h){
    const iw=img.videoWidth||img.width||w, ih=img.videoHeight||img.height||h;
    const scale=Math.min(w/iw,h/ih), dw=iw*scale, dh=ih*scale, x=(w-dw)/2,y=(h-dh)/2;
    ctx.save(); Effects.applyCanvasFilter(ctx); ctx.drawImage(img,x,y,dw,dh); ctx.restore();
  },
  drawOverlays(ctx,ms,w,h){
    State.texts.filter(t=>ms>=t.startMs && ms<=t.startMs+t.durationMs).forEach(t=>{
      ctx.save(); ctx.font='bold '+(t.size||48)+'px Arial'; ctx.textAlign='center';
      ctx.lineWidth=Math.max(4,(t.size||48)/12); ctx.strokeStyle='rgba(0,0,0,.75)'; ctx.fillStyle=t.color||'#fff';
      const y=h*0.78; ctx.strokeText(t.text,w/2,y); ctx.fillText(t.text,w/2,y); ctx.restore();
    });
  },
  drawHud(ctx,clip,i,w,h){
    ctx.save(); ctx.fillStyle='rgba(0,0,0,.35)'; ctx.fillRect(16,16,Math.min(w-32,420),42);
    ctx.fillStyle='#fff'; ctx.font='22px Arial'; ctx.fillText('TradeUp • '+(i+1)+'/'+State.videos.length,28,45); ctx.restore();
  },
  progress(p,msg){ document.getElementById('exportBar').style.width=Math.max(0,Math.min(100,p))+'%'; document.getElementById('exportStatus').textContent=msg||''; }
};
function setExportProgress(p,msg){ Exporter.progress(p,msg); }
