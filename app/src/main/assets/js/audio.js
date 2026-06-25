const AudioTools = {
  setVolume(v){ State.settings.musicVolume=Number(v)/100; if(Player.audio)Player.audio.volume=State.settings.musicVolume; Timeline.render(); Project.autosave(); document.getElementById('audioInfo').textContent='Volume da música: '+v+'%'; },
  setFade(){ State.settings.fadeIn=Number(document.getElementById('fadeIn').value||0); State.settings.fadeOut=Number(document.getElementById('fadeOut').value||0); document.getElementById('audioInfo').textContent=`Fade in ${State.settings.fadeIn}s • Fade out ${State.settings.fadeOut}s`; }
};
function setAudioTrack(audio){ State.audio=audio; State.settings.musicVolume=Number(document.getElementById('musicVolume').value||70)/100; Timeline.render(); Media.render(); Project.autosave(); document.getElementById('audioInfo').textContent='Música: '+(audio.name||'sem nome'); }
