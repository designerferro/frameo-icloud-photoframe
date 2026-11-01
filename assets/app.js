'use strict';
let csrf='',first=false;
const byId=id=>document.getElementById(id);
const message=(text,error=false)=>{byId('message').textContent=text;byId('message').className=error?'error':'';};
async function api(path,data){
 const options={credentials:'same-origin',cache:'no-store',headers:{}};
 if(data!==undefined){options.method='POST';options.headers['Content-Type']='application/json';options.headers['X-CSRF-Token']=csrf;options.body=JSON.stringify(data);}
 const controller=new AbortController();options.signal=controller.signal;const timeout=setTimeout(()=>controller.abort(),15000);
 let response;try{response=await fetch(path,options);}catch(error){throw new Error('A moldura não respondeu. Verifica a ligação e tenta novamente.');}finally{clearTimeout(timeout);}
 const body=await response.json();
 if(!response.ok||body.error){if(response.status===401)show('login');throw new Error(body.error||'Não foi possível executar a operação.');}return body;
}
function show(page){for(const name of ['login','credentials','configuration'])byId(name).hidden=name!==page;}
function formData(id){return new FormData(byId(id));}
function render(state,fill=true){
 byId('state').textContent=state.status+' · '+state.count+' fotografias e filmes'+(state.busy?' · A sincronizar…':'');
 byId('versions').textContent='APK instalado: '+state.installed+' · Última release: '+state.latest+(state.updateStatus?' · '+state.updateStatus:'');
 byId('albumState').textContent=state.albumConfigured?'Álbum configurado.':'Nenhum álbum configurado.';
 byId('durationLimit').textContent='Máximo atual: '+state.maxVideoSeconds+' segundos, calculado pelo espaço disponível. Por omissão: 30.';
 const form=byId('settingsForm');form.elements.videoSeconds.max=state.maxVideoSeconds;
 if(fill){form.elements.crop.value=String(state.crop);form.elements.orientation.value=String(state.orientation);form.elements.order.value=state.order;form.elements.videoSeconds.value=state.videoSeconds;form.elements.volume.value=state.volume;form.elements.autoUpdate.checked=state.autoUpdate;form.elements.remoteEnabled.checked=state.remoteEnabled;byId('volumeValue').textContent=state.volume;}
}
async function refresh(fill=true){render(await api('/api/state'),fill);}
async function openSession(){const session=await api('/api/session');csrf=session.csrf;first=session.firstAccess;byId('cancelCredentials').hidden=first;show(first?'credentials':'configuration');if(!first)await refresh();}
function guarded(action){return async event=>{event.preventDefault();const button=event.submitter||event.currentTarget;if(button.tagName==='BUTTON')button.disabled=true;try{await action(event);}catch(error){message(error.message,true);}finally{button.disabled=false;}};}
byId('loginForm').addEventListener('submit',guarded(async()=>{const data=formData('loginForm');const session=await api('/api/login',{username:data.get('username'),password:data.get('password')});byId('loginForm').reset();csrf=session.csrf;await openSession();message(session.firstAccess?'Altera as credenciais para continuar.':'Sessão iniciada.');}));
byId('credentialsForm').addEventListener('submit',guarded(async()=>{const data=formData('credentialsForm');if(data.get('password')!==data.get('confirm'))throw new Error('As palavras-passe não coincidem.');await api('/api/credentials',{username:data.get('username'),password:data.get('password')});byId('credentialsForm').reset();csrf='';show('login');message('Credenciais guardadas. Inicia sessão com os novos dados.');}));
byId('settingsForm').addEventListener('submit',guarded(async()=>{const data=formData('settingsForm');if(!data.has('remoteEnabled')&&!confirm('Desativar o acesso remoto? Só poderás voltar a ativá-lo na moldura.'))return;const state=await api('/api/settings',{crop:data.get('crop')==='true',orientation:Number(data.get('orientation')),order:data.get('order'),videoSeconds:Number(data.get('videoSeconds')),volume:Number(data.get('volume')),autoUpdate:data.has('autoUpdate'),remoteEnabled:data.has('remoteEnabled')});render(state);message('Configurações guardadas na moldura.');}));
byId('settingsForm').elements.volume.addEventListener('input',event=>{byId('volumeValue').textContent=event.target.value;});
byId('albumForm').addEventListener('submit',guarded(async()=>{const data=formData('albumForm');const state=await api('/api/action',{action:'album',url:data.get('url'),clear:data.has('clear')});byId('albumForm').reset();render(state,false);message('Álbum guardado. A ligação não é apresentada.');}));
for(const button of document.querySelectorAll('[data-action]'))button.addEventListener('click',guarded(async()=>{const state=await api('/api/action',{action:button.dataset.action});render(state,false);message(button.dataset.action==='android'?'Definições Android abertas na moldura.':'Pedido enviado à moldura.');}));
byId('clearAlbum').addEventListener('click',guarded(async()=>{if(!confirm('Apagar a ligação e todos os ficheiros locais? O álbum no iCloud não será alterado.'))return;render(await api('/api/action',{action:'clear',confirm:true}),false);message('Álbum e ficheiros locais removidos.');}));
byId('reinstall').addEventListener('click',guarded(async()=>{if(!confirm('Procurar a última release e reinstalar se já estiver instalada? Os dados serão mantidos.'))return;await api('/api/action',{action:'update',reinstall:true});message('Pedido de atualização / reinstalação enviado.');}));
byId('refresh').addEventListener('click',guarded(async()=>{await refresh(false);message('Estado atualizado.');}));
byId('changeCredentials').addEventListener('click',()=>{byId('cancelCredentials').hidden=false;show('credentials');});
byId('cancelCredentials').addEventListener('click',()=>show('configuration'));
byId('logout').addEventListener('click',guarded(async()=>{await api('/api/logout',{});csrf='';show('login');message('Sessão terminada.');}));
openSession().catch(()=>show('login'));
