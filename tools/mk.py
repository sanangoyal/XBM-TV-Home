import re,json,base64,os
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S=ROOT+'/web/src'
icons={}
for n in ['youtube','appletv','applemusic','spotify','googlechrome','googleplay','plex','kodi','vlcmediaplayer','discord']:
    s=open(S+'/si/node_modules/simple-icons/icons/%s.svg'%n).read();icons[n]=re.search(r' d="([^"]+)"',s).group(1)
def uri(p,fix=None):
    s=open(p,encoding='utf-8').read()
    if fix:s=fix(s)
    return 'data:image/svg+xml;base64,'+base64.b64encode(s.encode()).decode()
logos={
 'netflix':uri(S+'/zip/Netflix_2016_N_logo.svg'),
 'prime':uri(S+'/z17/Amazon_Prime_Video_blue_logo_1.svg',lambda s:re.sub(r'<sodipodi:namedview[^>]*/>','',s).replace('viewBox="0 0 480 480"','viewBox="92 93 295 295" width="295" height="295"').replace('<path d=','<path fill="#00A8E1" d=').replace('<rect ','<rect fill="#00A8E1" ')),
 'youtube':uri(S+'/z17/YouTube_full-color_icon_(2017).svg',lambda s:s.replace('width: 100%; height: 100%;','width:100%;height:100%;').replace('<svg xmlns="http://www.w3.org/2000/svg" class="external-icon" viewBox="0 0 28.57  20"','<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 28.57 20" width="285" height="200"',1)),
 'hotstar':uri(S+'/zip/jiohotstar-seeklogo.svg',lambda s:s.replace('<path ','<path fill="#fff" '))}
import json as J
ics=J.load(open(S+'/z16/icons.json'))
sheet=''.join('<g id="%s">%s</g>'%(i['id'],i['paths']) for i in ics)
ALIAS={'Layer_32':'g1_04','Layer_39':'g1_27','music':'g1_25','game':'g2_01','folder':'g2_02','Layer_5':'g1_18','Layer_8':'g1_17','Layer_12_copy':'g1_21','Layer_29':'g1_15','Layer_26':'g1_11','Layer_76':'g1_13','Layer_28':'g1_12','powersave':'g1_14','Layer_9':'g1_19','bd':'g2_06','bd-normal':'g2_06','Layer_27':'g2_04','trophy':'g2_05','Layer_42':'g1_28','Layer_41':'g1_24','Layer_40':'g1_20','user_home':'g1_01','Layer_20':'g1_06','Layer_36':'g1_23','Layer_33':'g1_16','Layer_11':'g1_22','Layer_19':'g1_08','Layer_19_copy':'g1_05','Layer_43':'g2_09','Layer_34':'g2_10','Layer_53':'g3_11','headset':'g3_09','acc_information':'g3_03','Layer_69':'g1_07','Layer_61':'g1_10','Layer_22':'g1_09','user':'g1_03'}
t=open(ROOT+'/web/xmb.tpl.html').read().replace('/*ALIAS*/',J.dumps(ALIAS))
t=t.replace('/*SNDB64*/{}',open(S+'/bs/snd_final.json').read())
t=t.replace('/*SHEET*/',sheet).replace('/*ICONS*/',json.dumps(icons)).replace('/*LOGOS*/',json.dumps(logos))

import json as _j,re as _re,base64 as _b
CF=S+'/cf/app/assets/icons/';Z=S+'/z18/mono/';SI=S+'/si/node_modules/simple-icons/icons/'
SRC={'netflix':CF+'netflix.svg','youtube':CF+'youtube.svg','primevideo':CF+'primevideo.svg','appletv':CF+'appletv.svg','applemusic':CF+'applemusic.svg','spotify':CF+'spotify.svg'}
for n in ['hotstar','zee5','sonyliv','googlechrome','googleplay','plex','kodi','vlcmediaplayer','discord']: SRC[n]=Z+n+'.svg'
FIN=_j.load(open(S+'/logo_final.json'));LGD={}
for n,f in SRC.items():
    s=open(f).read();s=s if 'fill=' in s else s.replace('<svg ','<svg fill="#ffffff" ',1)
    LGD[n]=dict(u='data:image/svg+xml;base64,'+_b.b64encode(s.encode()).decode(),w=FIN[n]['ew'],h=FIN[n]['eh'],dx=FIN[n]['dx'],dy=FIN[n]['dy'])
_y=open(S+'/yt/Asset 1YTmusic.svg').read();_y=_re.sub(r'<defs>.*?</defs>','',_y,flags=_re.S).replace('class="cls-1"','fill="#fff"')
LGD['ytmusic']=dict(u='data:image/svg+xml;base64,'+_b.b64encode(_y.encode()).decode(),w=58,h=58,dx=0,dy=0)
for _k,_f in (('camera','camera'),('files','files'),('browse','browse'),('bt','bt'),('livetv','livetv')):
    _x=open(S+'/own/'+_f+'.svg').read()
    LGD[_k]=dict(u='data:image/svg+xml;base64,'+_b.b64encode(_x.encode()).decode(),w=62,h=62,dx=0,dy=0)
LGD['ytmusic']['w']=LGD['ytmusic']['h']=62
def _wh(s):
    s=_re.sub(r'fill="(?!none)[^"]*"','fill="#fff"',s);s=_re.sub(r'(fill|stroke):\s*(?!none)[^;"]+',lambda x:x.group(1)+':#fff',s);s=_re.sub(r'stroke="(?!none)[^"]*"','stroke="#fff"',s)
    if not _re.search(r'<svg[^>]* fill=',s):s=s.replace('<svg ','<svg fill="#fff" ',1)
    return s
for _k,_v in LGD.items():
    _s=_b.b64decode(_v['u'].split(',',1)[1]).decode('utf-8','ignore');_v['u']='data:image/svg+xml;base64,'+_b.b64encode(_wh(_s).encode()).decode()
t=t.replace('/*LG*/{}',_j.dumps(LGD))
key=''
try: key=open(ROOT+'/android/tmdb.key').read().strip()
except Exception: print('note: android/tmdb.key missing, movie scenes disabled')
t=t.replace('%%TMDBKEY%%',key)
open(ROOT+'/android/assets/index.html','w').write(t)
print(len(t))
