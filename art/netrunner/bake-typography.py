#!/usr/bin/env python3
"""Bake licensed Rajdhani glyphs at 48 px for the native, linearly filtered HUD."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import json
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'standalone/tensura-additional-additions/src/main/resources/assets/heavenlyrestriction'
for face,file in [('semibold','scanner.ttf'),('medium','scanner_medium.ttf')]:
    font=ImageFont.truetype(str(ASSETS/'font'/file),48)
    atlas=Image.new('RGBA',(1024,1024)); draw=ImageDraw.Draw(atlas)
    glyphs={}; x=y=0; row=0
    chars=[chr(i) for i in range(32,256)]+['•','…','—','–','↻','▦']
    for ch in chars:
        left,top,right,bottom=font.getbbox(ch,anchor='ls'); w,h=max(1,right-left)+16,max(1,bottom-top)+16
        if x+w>1024:x=0;y+=row;row=0
        assert y+h<=1024
        draw.text((x+8-left,y+8-top),ch,font=font,fill='white',anchor='ls')
        glyphs[str(ord(ch))]={'x':x,'y':y,'w':w,'h':h,'left':left-8,'top':top-8,'advance':font.getlength(ch)}
        x+=w;row=max(row,h)
    path=ASSETS/'textures/gui/netrunner'/('type_'+face+'.png');atlas.save(path)
    glow=atlas.filter(ImageFilter.GaussianBlur(3));glow.save(path.with_name('type_'+face+'_glow.png'))
    # Prefilter at lower resolutions: bilinear sampling of the full atlas drops fine strokes.
    for pixels,dimension in [(24,512),(12,256),(6,128)]:
        atlas.resize((dimension,dimension),Image.Resampling.LANCZOS).save(path.with_name('type_'+face+'_'+str(pixels)+'.png'))
        glow.resize((dimension,dimension),Image.Resampling.LANCZOS).save(path.with_name('type_'+face+'_glow_'+str(pixels)+'.png'))
    kern={}
    for a in chars[:95]:
        for b in chars[:95]:
            delta=font.getlength(a+b)-font.getlength(a)-font.getlength(b)
            if abs(delta)>.03:kern[a+b]=round(delta,4)
    (ASSETS/'scanner'/('type_'+face+'.json')).write_text(json.dumps({'size':48,'width':1024,'height':1024,'glyphs':glyphs,'kerning':kern},separators=(',',':'))+'\n')
