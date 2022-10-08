let SessionLoad = 1
let s:so_save = &g:so | let s:siso_save = &g:siso | setg so=0 siso=0 | setl so=-1 siso=-1
let v:this_session=expand("<sfile>:p")
silent only
silent tabonly
cd ~/documents/instagram
if expand('%') == '' && !&modified && line('$') <= 1 && getline(1) == ''
  let s:wipebuf = bufnr('%')
endif
let s:shortmess_save = &shortmess
if &shortmess =~ 'A'
  set shortmess=aoOA
else
  set shortmess=aoO
endif
badd +8 cookie.txt
badd +3 graphql.query
badd +39 get_saved.js
badd +51 term://~/documents/instagram//15169:node\ get_saved.js
badd +1 test.js
badd +19 term://~/documents/instagram//16644:node\ get_saved.js
badd +185 ~/github/configs/nvim/init.vim
badd +249 term://~/documents/instagram//17372:node\ get_saved.js
badd +68 term://~/documents/instagram//17752:node\ get_saved.js
argglobal
%argdel
edit get_saved.js
argglobal
setlocal fdm=indent
setlocal fde=0
setlocal fmr={{{,}}}
setlocal fdi=#
setlocal fdl=0
setlocal fml=1
setlocal fdn=10
setlocal fen
5
normal! zo
40
normal! zo
let s:l = 10 - ((9 * winheight(0) + 11) / 23)
if s:l < 1 | let s:l = 1 | endif
keepjumps exe s:l
normal! zt
keepjumps 10
normal! 0
tabnext 1
if exists('s:wipebuf') && len(win_findbuf(s:wipebuf)) == 0 && getbufvar(s:wipebuf, '&buftype') isnot# 'terminal'
  silent exe 'bwipe ' . s:wipebuf
endif
unlet! s:wipebuf
set winheight=1 winwidth=20
let &shortmess = s:shortmess_save
let s:sx = expand("<sfile>:p:r")."x.vim"
if filereadable(s:sx)
  exe "source " . fnameescape(s:sx)
endif
let &g:so = s:so_save | let &g:siso = s:siso_save
set hlsearch
nohlsearch
doautoall SessionLoadPost
unlet SessionLoad
" vim: set ft=vim :
let g:CJWinBuffs = {'1017': ['get_saved.js', 'term://~/documents/instagram//17752:node get_saved.js', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'test.js', 'term://~/documents/instagram//16644:node get_saved.js', '/home/chinmay/github/configs/nvim/init.vim', 'term://~/documents/instagram//17372:node get_saved.js'], '1087': ['term://~/documents/instagram//16570:node get_saved.js', 'get_saved.js'], '1031': ['term://~/documents/instagram//17841:node get_saved.js', 'get_saved.js'], '1026': ['term://~/documents/instagram//17820:node get_saved.js', 'get_saved.js'], '1259': ['term://~/documents/instagram//17327:node get_saved.js', 'get_saved.js'], '1272': ['term://~/documents/instagram//17430:node get_saved.js', 'get_saved.js'], '1166': ['term://~/documents/instagram//17109:node get_saved.js', 'get_saved.js'], '1262': ['term://~/documents/instagram//17349:node get_saved.js', 'get_saved.js'], '1223': ['term://~/documents/instagram//17197:node get_saved.js', 'get_saved.js'], '1057': ['term://~/documents/instagram//16401:node test.js', 'test.js'], '1001': ['get_saved.js', '/home/chinmay/github/configs/nvim/init.vim', 'get_saved.js', 'test.js', 'term://~/documents/instagram//16824:://node test.js | startinsert', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'term://~/documents/instagram//16644:node get_saved.js', 'term://~/documents/instagram//17372:node get_saved.js'], '1132': ['term://~/documents/instagram//16913:node test.js', 'test.js'], '1079': ['term://~/documents/instagram//16508:node test.js', 'test.js'], '1135': ['term://~/documents/instagram//16958:node get_saved.js', 'get_saved.js'], '1136': ['term://~/documents/instagram//16983:node get_saved.js', 'get_saved.js'], '1092': ['term://~/documents/instagram//16590:node get_saved.js', 'get_saved.js'], '1093': ['term://~/documents/instagram//16612:node get_saved.js', 'get_saved.js', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'term://~/documents/instagram//15207:bash', 'test.js'], '1000': ['get_saved.js', 'term://~/documents/instagram//16788:://node test.js | startinsert', 'test.js', 'term://~/documents/instagram//16788:://node test.js | startinsert', 'get_saved.js', '/home/chinmay/github/configs/nvim/init.vim', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'term://~/documents/instagram//15207:bash', 'term://~/documents/instagram//16644:node get_saved.js'], '1265': ['get_saved.js', 'term://~/documents/instagram//17372:node get_saved.js', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'test.js', 'term://~/documents/instagram//16644:node get_saved.js', '/home/chinmay/github/configs/nvim/init.vim'], '1042': ['get_saved.js', 'term://~/documents/instagram//15169:node get_saved.js', 'get_saved.js', 'cookie.txt', 'graphql.query'], '1044': ['term://~/documents/instagram//15207:bash', 'get_saved.js'], '1105': ['get_saved.js', 'term://~/documents/instagram//16644:node get_saved.js', 'cookie.txt', 'graphql.query', 'term://~/documents/instagram//15169:node get_saved.js', 'term://~/documents/instagram//15207:bash', 'test.js'], '1023': ['term://~/documents/instagram//16377:node test.js', 'test.js']}
let g:CJLoadedSeshName = '/home/chinmay/documents/instagram'
