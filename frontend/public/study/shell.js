/* /study 노트 공통 셸.
   노트 파일은 <main id="mainContent" data-title data-stages> 안에 <article class="doc" id data-stage> 만 든다.
   여기서 앱바·사이드바·학습 완료·진행률·이전/다음·테마 토글을 그 article 목록으로 만든다.

   저장 키 — study:<slug>:progress (절 id → true) · study:<slug>:last (마지막 절) · study:nav (사이드바 열림, 노트 공통)
   테마 키 — theme ('dark' | 'light'). 목록(/study)의 AppShell 과 같은 키라 서로 따라간다.
   data-overview 가 붙은 article 은 단계 개요다. 목차의 단계 이름을 누르면 열리고, 진행률에는 안 센다. */
(function(){
  var main = document.getElementById('mainContent');
  if(!main) return;
  var docs = Array.prototype.slice.call(main.querySelectorAll('article.doc'));
  if(!docs.length) return;

  var slug = location.pathname.split('/').filter(Boolean).pop().replace(/\.html$/, '') || 'note';
  var KEY_PROGRESS = 'study:' + slug + ':progress';
  var KEY_LAST     = 'study:' + slug + ':last';
  var KEY_NAV      = 'study:nav';
  var KEY_THEME    = 'theme';
  var NARROW = 1060;

  function get(k){ try{ return localStorage.getItem(k); }catch(e){ return null; } }
  function set(k,v){ try{ localStorage.setItem(k,v); }catch(e){} }
  function el(tag, cls, text){
    var n = document.createElement(tag);
    if(cls) n.className = cls;
    if(text != null) n.textContent = text;
    return n;
  }

  if('scrollRestoration' in history) history.scrollRestoration = 'manual';

  /* ── 단계 이름표: data-stages="S1:모델|S2:사용자" ── */
  var stageLabel = {};
  (main.getAttribute('data-stages') || '').split('|').forEach(function(pair){
    var i = pair.indexOf(':');
    if(i > 0) stageLabel[pair.slice(0,i).trim()] = pair.slice(i+1).trim();
  });

  /* ── 절 목록 ── */
  var ORDER = docs.map(function(a){
    var h = a.querySelector('h1');
    return {
      id: a.id,
      el: a,
      title: h ? h.textContent.trim() : a.id,
      stage: a.getAttribute('data-stage') || '',
      overview: a.hasAttribute('data-overview')
    };
  });
  var byId = {};
  ORDER.forEach(function(o){ byId[o.id] = o; });
  var countable = ORDER.filter(function(o){ return !o.overview; });

  var progress = {};
  try{ progress = JSON.parse(get(KEY_PROGRESS) || '{}') || {}; }catch(e){ progress = {}; }

  /* ── 본문을 .main-inner 로 감싸고 pager 를 붙인다 ── */
  var inner = el('div', 'main-inner');
  while(main.firstChild) inner.appendChild(main.firstChild);
  main.appendChild(inner);
  var pager = el('div', 'pager'); pager.id = 'pager';
  inner.appendChild(pager);
  main.setAttribute('tabindex', '-1');

  /* ── 표를 가로 스크롤 상자로 ── */
  inner.querySelectorAll('table').forEach(function(t){
    if(t.parentNode && t.parentNode.className === 'table-wrap') return;
    var w = el('div', 'table-wrap');
    t.parentNode.insertBefore(w, t);
    w.appendChild(t);
  });

  /* ── 앱바 ── */
  var title = main.getAttribute('data-title') || document.title;
  var skip = el('a', 'skip', '본문 바로가기'); skip.href = '#mainContent';
  var bar = el('header', 'app-bar');
  var burger = el('button', 'icon-btn'); burger.type = 'button'; burger.id = 'burger';
  burger.setAttribute('aria-controls', 'sidebar');
  burger.innerHTML = '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M2 4h12M2 8h12M2 12h12"/></svg>';
  var back = el('a', 'bar-back', '← 목록'); back.href = '/study';
  var barTitle = el('span', 'bar-title', title);
  var barHere = el('span', 'bar-here', ''); barHere.id = 'barHere';
  var pill = el('div', 'progress-pill');
  var doneCount = el('b', null, '0'); doneCount.id = 'doneCount';
  pill.appendChild(doneCount); pill.appendChild(document.createTextNode('/' + countable.length + ' 완료'));
  var reset = el('button', 'chip-btn', '진행률 초기화'); reset.type = 'button'; reset.id = 'resetProgressBtn';
  var themeBtn = el('button', 'theme-btn'); themeBtn.type = 'button'; themeBtn.id = 'themeBtn';
  bar.appendChild(burger); bar.appendChild(back); bar.appendChild(barTitle);
  bar.appendChild(el('span', 'bar-sep', '/')); bar.appendChild(barHere);
  bar.appendChild(pill); bar.appendChild(reset); bar.appendChild(themeBtn);

  /* ── 사이드바 ── */
  var sidebar = el('aside', 'sidebar'); sidebar.id = 'sidebar'; sidebar.setAttribute('aria-label', '목차');
  var nav = el('nav'); nav.id = 'nav';
  sidebar.appendChild(nav);
  var navFor = {};
  var groups = [];
  ORDER.forEach(function(o){
    var g = groups.length && groups[groups.length-1].stage === o.stage ? groups[groups.length-1] : null;
    if(!g){ g = { stage:o.stage, items:[] }; groups.push(g); }
    g.items.push(o);
  });
  groups.forEach(function(g){
    var wrap = el('div', 'nav-group');
    var overview = g.items.filter(function(o){ return o.overview; })[0];
    var labelText = stageLabel[g.stage] || g.stage;
    if(g.stage && labelText !== g.stage) labelText = g.stage + ' · ' + labelText;
    var lab;
    if(overview){
      lab = el('button', 'nav-group-label', labelText); lab.type = 'button';
      lab.addEventListener('click', function(){ go(overview.id, true); });
      navFor[overview.id] = lab;
    } else {
      lab = el('div', 'nav-group-label', labelText);
    }
    wrap.appendChild(lab);
    g.items.forEach(function(o){
      if(o.overview) return;
      var b = el('button', 'nav-link' + (progress[o.id] ? ' done' : '')); b.type = 'button';
      b.appendChild(el('span', 'dot'));
      b.appendChild(document.createTextNode(o.title));
      b.addEventListener('click', function(){ go(o.id, true); });
      navFor[o.id] = b;
      wrap.appendChild(b);
    });
    nav.appendChild(wrap);
  });
  var foot = el('div', 'nav-foot', title + ' · ' + countable.length + '절');
  sidebar.appendChild(foot);

  document.body.insertBefore(sidebar, main);
  document.body.insertBefore(bar, sidebar);
  document.body.insertBefore(skip, bar);

  /* ── 절마다 학습 완료 버튼 ── */
  var doneBtnFor = {};
  countable.forEach(function(o){
    var h = o.el.querySelector('h1');
    if(!h) return;
    var tools = el('div', 'page-tools');
    var done = el('button', 'done-btn'); done.type = 'button';
    done.setAttribute('aria-pressed', progress[o.id] ? 'true' : 'false');
    done.innerHTML = '<span class="box"></span>학습 완료';
    done.addEventListener('click', function(){
      var now = done.getAttribute('aria-pressed') !== 'true';
      if(now) progress[o.id] = true; else delete progress[o.id];
      done.setAttribute('aria-pressed', now ? 'true' : 'false');
      set(KEY_PROGRESS, JSON.stringify(progress));
      if(navFor[o.id]) navFor[o.id].classList.toggle('done', now);
      badgeCount();
    });
    doneBtnFor[o.id] = done;
    tools.appendChild(done);
    var anchor = h.nextElementSibling;
    /* 판정 질문·리드 문단 뒤에 둔다. 없으면 제목 바로 뒤 */
    while(anchor && (anchor.classList.contains('q') || anchor.classList.contains('doc-lead'))) anchor = anchor.nextElementSibling;
    o.el.insertBefore(tools, anchor);
  });

  function badgeCount(){
    var d = countable.filter(function(o){ return progress[o.id]; }).length;
    doneCount.textContent = d;
  }
  badgeCount();

  reset.addEventListener('click', function(){
    progress = {};
    set(KEY_PROGRESS, '{}');
    Object.keys(doneBtnFor).forEach(function(id){ doneBtnFor[id].setAttribute('aria-pressed', 'false'); });
    Object.keys(navFor).forEach(function(id){ navFor[id].classList.remove('done'); });
    badgeCount();
  });

  /* ── 테마: 목록과 같은 키 ── */
  /* 원이 퍼지는 속도 곡선. AppShell 과 같은 값이다 */
  var REVEAL_EASING = 'linear(0, 0.034, 0.114, 0.215, 0.321, 0.423, 0.516, 0.599, 0.671, 0.732, 0.783, 0.825, 0.86, 0.888, 0.911, 0.93, 0.944, 0.956, 0.966, 0.973, 0.979, 0.983, 0.987, 0.99, 0.992, 0.994, 0.995, 0.996, 0.997, 0.998, 0.998, 0.999, 1)';
  function applyTheme(dark){
    if(dark) document.documentElement.setAttribute('data-theme', 'dark');
    else document.documentElement.removeAttribute('data-theme');
    themeBtn.textContent = dark ? '☀ LIGHT' : '☾ DARK';
  }
  applyTheme(get(KEY_THEME) === 'dark');
  /* 새 테마 화면을 버튼 중심에서 원으로 넓혀 덮는다. View Transitions 가 없거나 동작 줄이기 설정이면 바로 바꾼다 */
  themeBtn.addEventListener('click', function(){
    var dark = document.documentElement.getAttribute('data-theme') !== 'dark';
    set(KEY_THEME, dark ? 'dark' : 'light');
    if(!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches){
      applyTheme(dark);
      return;
    }
    var r = themeBtn.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    var transition = document.startViewTransition(function(){ applyTheme(dark); });
    transition.ready.then(function(){
      document.documentElement.animate(
        { clipPath: ['circle(0 at ' + x + 'px ' + y + 'px)', 'circle(' + radius + 'px at ' + x + 'px ' + y + 'px)'] },
        { duration: 500, easing: REVEAL_EASING, pseudoElement: '::view-transition-new(root)' }
      );
    });
  });
  window.addEventListener('storage', function(e){ if(e.key === KEY_THEME) applyTheme(e.newValue === 'dark'); });

  /* ── 절 전환: 한 절만 보인다 ── */
  function go(id, fromClick){
    var hit = byId[id];
    if(!hit) return;
    ORDER.forEach(function(o){
      o.el.classList.toggle('on', o.id === id);
      if(navFor[o.id]) navFor[o.id].setAttribute('aria-current', o.id === id ? 'true' : 'false');
    });
    barHere.textContent = hit.title;
    buildPager(id);
    set(KEY_LAST, id);
    if(location.hash !== '#' + id) history.replaceState(null, '', '#' + id);
    window.scrollTo({ top:0, behavior: fromClick ? 'smooth' : 'auto' });
    /* 본문이 통째로 바뀌었는데 초점이 목차에 남으면 화면낭독기에는 아무 일도 안 일어난 것과 같다 */
    if(fromClick){
      var h = hit.el.querySelector('h1');
      if(h){ h.setAttribute('tabindex', '-1'); h.focus({ preventScroll:true }); }
    }
    if(fromClick && window.innerWidth < NARROW) setNav(false);
  }

  function buildPager(id){
    var i = -1;
    ORDER.forEach(function(o, idx){ if(o.id === id) i = idx; });
    pager.innerHTML = '';
    function btn(target, label, cls){
      var b = el('button', cls || null); b.type = 'button';
      b.appendChild(el('span', null, label));
      b.appendChild(el('em', null, target ? target.title : '—'));
      b.disabled = !target;
      if(target) b.addEventListener('click', function(){ go(target.id, true); });
      return b;
    }
    pager.appendChild(btn(i > 0 ? ORDER[i-1] : null, '이전'));
    pager.appendChild(btn(i >= 0 && i < ORDER.length-1 ? ORDER[i+1] : null, '다음', 'next'));
  }

  /* ── 사이드바 열고 닫기. 스크림이 없어 열려 있어도 본문이 산다 ── */
  function setNav(open){
    sidebar.classList.toggle('open', open);
    main.classList.toggle('shifted', open);
    burger.setAttribute('aria-expanded', open ? 'true' : 'false');
    burger.setAttribute('aria-label', open ? '목차 닫기' : '목차 열기');
    set(KEY_NAV, open ? '1' : '0');
  }
  burger.addEventListener('click', function(){ setNav(!sidebar.classList.contains('open')); });
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape' && sidebar.classList.contains('open')) setNav(false);
  });
  var savedNav = get(KEY_NAV);
  setNav(savedNav !== null ? savedNav === '1' : window.innerWidth >= NARROW);

  /* ── 첫 진입: 해시 → 마지막 절 → 첫 절 ── */
  var want = location.hash.slice(1);
  if(!byId[want]) want = get(KEY_LAST);
  go(byId[want] ? want : ORDER[0].id, false);
  window.addEventListener('hashchange', function(){
    var h = location.hash.slice(1);
    if(byId[h]) go(h, false);
  });
})();
