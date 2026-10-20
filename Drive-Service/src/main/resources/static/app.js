const state = { folderId: null, path: [], sharedOwnerId:null, sharedOwnerEmail:null, token: localStorage.getItem('driveAccessToken'), authMode: 'login', view:'drive', page:0, pageSize:20, query:'', sort:'name', direction:'asc', layout:'list', loadRequest:0, navigation:[{folderId:null,path:[],sharedOwnerId:null,sharedOwnerEmail:null}], navigationIndex:0 };
    const itemsEl = document.getElementById('items');
    const statusEl = document.getElementById('status');
    const emptyEl = document.getElementById('empty');
    const tableWrap = document.getElementById('tableWrap');
    const toastEl = document.getElementById('toast');
    let toastTimer;

    function setStatus(message, type = '') {
      statusEl.textContent = message;
      statusEl.className = `status ${type}`;
    }
    function notify(message) {
      toastEl.textContent = message;
      toastEl.classList.add('show');
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2800);
    }
    function formatSize(bytes) {
      if (bytes < 1024) return `${bytes} B`;
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
      if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
      return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }
    function formatDate(value) {
      if (!value) return '—';
      return new Intl.DateTimeFormat(undefined, {month:'short', day:'numeric', year:'numeric'}).format(new Date(value));
    }
    function iconFor(item) {
      if (item.kind === 'FOLDER') return '📁';
      const ext = item.name.split('.').pop().toLowerCase();
      if (['jpg','jpeg','png','gif','webp','svg'].includes(ext)) return '🖼️';
      if (['pdf'].includes(ext)) return '📕';
      if (['doc','docx','txt','md'].includes(ext)) return '📄';
      if (['zip','rar','7z'].includes(ext)) return '🗜️';
      return '📃';
    }
    async function request(url, options = {}) {
      const headers = new Headers(options.headers || {});
      if (state.token && !url.startsWith('/api/auth/')) headers.set('Authorization', `Bearer ${state.token}`);
      let response = await fetch(url, {...options, headers, credentials:'same-origin'});
      if (response.status === 401 && state.token && !url.startsWith('/api/auth/')) {
        const refreshed = await fetch('/api/auth/refresh', {method:'POST', credentials:'same-origin'});
        if (refreshed.ok) {
          const session = await refreshed.json();
          state.token = session.accessToken;
          localStorage.setItem('driveAccessToken', session.accessToken);
          headers.set('Authorization', `Bearer ${state.token}`);
          response = await fetch(url, {...options, headers, credentials:'same-origin'});
        } else {
          state.token = null;
          localStorage.removeItem('driveAccessToken'); localStorage.removeItem('driveEmail');
          setAuthMode('login'); showDrive();
          throw new Error('Your session expired. Please sign in again.');
        }
      }
      if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
          const body = await response.json();
          message = body.detail || body.message || body.title || message;
        } catch (_) { }
        throw new Error(message);
      }
      return response;
    }
    function renderBreadcrumbs() {
      const nav = document.getElementById('breadcrumbs');
      nav.replaceChildren();
      if (state.view === 'trash') {
        const label = document.createElement('span'); label.className = 'crumb current'; label.textContent = 'Trash'; nav.append(label);
        document.getElementById('locationLabel').textContent = 'Trash';
        document.getElementById('pageTitle').textContent = 'Trash';
        document.getElementById('folderTree').replaceChildren();
        document.getElementById('newFolder').classList.add('hidden');
        document.getElementById('uploadButton').classList.add('hidden');
        updateFolderNavigation();
        return;
      }
      const rootName = state.view === 'shared' ? 'Shared with me' : 'My Drive';
      const crumbs = [{name:rootName, type:'root'}];
      if (state.view === 'shared' && state.sharedOwnerId) crumbs.push({name:state.sharedOwnerEmail, type:'sharer'});
      state.path.forEach(folder => crumbs.push({...folder, type:'folder'}));
      crumbs.forEach((crumb, index) => {
        if (index) {
          const sep = document.createElement('span');
          sep.className = 'slash'; sep.textContent = '/'; nav.append(sep);
        }
        const button = document.createElement('button');
        button.className = `crumb ${index === crumbs.length - 1 ? 'current' : ''}`;
        button.textContent = crumb.name;
        button.addEventListener('click', () => {
          if (crumb.type === 'root') switchView(state.view === 'shared' ? 'shared' : 'drive');
          else if (crumb.type === 'sharer') navigateTo(null, []);
          else {
            const pathIndex = state.path.findIndex(folder => folder.id === crumb.id);
            navigateTo(crumb.id, state.path.slice(0, pathIndex + 1));
          }
        });
        nav.append(button);
      });
      const currentLocation = state.path.length ? state.path[state.path.length - 1].name : state.sharedOwnerEmail || rootName;
      document.getElementById('locationLabel').textContent = currentLocation;
      document.getElementById('pageTitle').textContent = currentLocation;
      const folderTree = document.getElementById('folderTree');
      folderTree.replaceChildren();
      state.path.forEach((folder, index) => {
        const button = document.createElement('button');
        button.type = 'button'; button.className = `tree-item ${index === state.path.length - 1 ? 'active' : ''}`;
        button.style.setProperty('--tree-depth', String(index));
        button.textContent = folder.name;
        button.addEventListener('click', () => navigateTo(folder.id, state.path.slice(0, index + 1)));
        folderTree.append(button);
      });
      document.getElementById('myDriveButton').classList.toggle('active', state.view === 'drive');
      document.getElementById('sharedButton').classList.toggle('active', state.view === 'shared');
      document.getElementById('trashButton').classList.toggle('active', state.view === 'trash');
      document.getElementById('uploadButtonLabel').textContent = state.path.length ? 'Upload here' : 'Upload files';
      const access = currentFolderAccess();
      const canEdit = access === 'OWNER' || access === 'EDITOR';
      document.getElementById('newFolder').classList.toggle('hidden', !canEdit || state.view === 'trash');
      document.getElementById('uploadButton').classList.toggle('hidden', !canEdit || state.view === 'trash');
      updateFolderNavigation();
    }
    function currentFolderAccess() {
      if (state.view === 'trash') return 'NONE';
      if (!state.folderId) return state.view === 'drive' ? 'OWNER' : 'NONE';
      return state.path.length ? (state.path[state.path.length - 1].accessLevel || 'OWNER') : 'OWNER';
    }
    function updateFolderNavigation() {
      const inTrash = state.view === 'trash';
      document.getElementById('backFolder').disabled = inTrash || state.navigationIndex <= 0;
      document.getElementById('forwardFolder').disabled = inTrash || state.navigationIndex >= state.navigation.length - 1;
      document.getElementById('upFolder').disabled = inTrash || (state.path.length === 0 && !(state.view === 'shared' && state.sharedOwnerId));
    }
    async function navigateTo(folderId, path) {
      if (state.view === 'trash') return;
      const nextPath = path.map(folder => ({...folder}));
      const currentPath = JSON.stringify(state.path);
      if (folderId !== state.folderId || JSON.stringify(nextPath) !== currentPath) {
        state.navigation = state.navigation.slice(0, state.navigationIndex + 1);
        state.navigation.push({folderId, path:nextPath, sharedOwnerId:state.sharedOwnerId, sharedOwnerEmail:state.sharedOwnerEmail});
        state.navigationIndex = state.navigation.length - 1;
      }
      state.folderId = folderId;
      state.path = nextPath;
      state.page = 0;
      return loadItems();
    }
    function moveInNavigation(offset) {
      if (state.view === 'trash') return;
      const nextIndex = state.navigationIndex + offset;
      if (nextIndex < 0 || nextIndex >= state.navigation.length) return;
      state.navigationIndex = nextIndex;
      const destination = state.navigation[nextIndex];
      state.folderId = destination.folderId;
      state.path = destination.path.map(folder => ({...folder}));
      state.sharedOwnerId = destination.sharedOwnerId || null;
      state.sharedOwnerEmail = destination.sharedOwnerEmail || null;
      state.page = 0;
      loadItems();
    }
    function actionButton(label, symbol, className, handler) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `icon-button ${className || ''}`;
      button.title = label;
      button.setAttribute('aria-label', label);
      const icon = ({'Version history':'◷','Download':'↓','Edit':'✎','Share':'↗','Move to Trash':'×','Open folder':'↗','Open shared items':'↗','Restore':'↶','Delete forever':'×','Restore this version':'↶'})[label] || symbol;
      const iconElement = document.createElement('span');
      iconElement.className = 'action-icon';
      iconElement.setAttribute('aria-hidden', 'true');
      iconElement.textContent = icon;
      const labelElement = document.createElement('span');
      labelElement.className = 'action-label';
      labelElement.textContent = label;
      button.append(iconElement, labelElement);
      button.addEventListener('click', handler);
      return button;
    }
    let lastRenderedItems = [];
    let lastRenderedTotal = 0;
    function renderLastItems() { render(lastRenderedItems, lastRenderedTotal); }
    function render(items, total = items.length) {
      lastRenderedItems = items; lastRenderedTotal = total;
      const inTrash = state.view === 'trash';
      itemsEl.replaceChildren();
      tableWrap.classList.toggle('grid-view', state.layout === 'grid');
      document.getElementById('itemCount').textContent = total;
      emptyEl.classList.toggle('hidden', items.length !== 0);
      tableWrap.classList.toggle('hidden', items.length === 0);
      const canEditFolder = currentFolderAccess() === 'OWNER' || currentFolderAccess() === 'EDITOR';
      const sharedRoot = state.view === 'shared' && !state.sharedOwnerId;
      const sharerRoot = state.view === 'shared' && Boolean(state.sharedOwnerId) && !state.folderId;
      document.getElementById('emptyTitle').textContent = inTrash ? 'Trash is empty' : sharedRoot ? 'Nothing shared with you yet' : sharerRoot ? 'No items shared by this person' : 'This folder is empty';
      document.getElementById('emptyDescription').textContent = inTrash ? 'Items you move to Trash will appear here.' : sharedRoot ? 'People who share files and folders with you will appear here.' : sharerRoot ? 'Items shared by this person will appear here.' : 'Upload a file or create a folder to get started.';
      document.getElementById('emptyUpload').classList.toggle('hidden', inTrash || !canEditFolder);
      document.getElementById('summaryLabel').textContent = inTrash ? 'Items in Trash' : sharedRoot ? 'People who shared with you' : 'Items in this folder';
      document.getElementById('modifiedHeading').textContent = inTrash ? 'Deleted' : 'Created';
      for (const item of items) {
        const row = document.createElement('tr');
        const nameCell = document.createElement('td');
        const nameWrap = document.createElement('div'); nameWrap.className = 'name-cell';
        const icon = document.createElement('span'); icon.className = `file-icon ${item.kind === 'FOLDER' ? 'folder' : ''}`; icon.textContent = iconFor(item);
        const name = document.createElement(item.kind === 'FOLDER' ? 'button' : 'span');
        name.className = item.kind === 'FOLDER' ? 'item-name open-folder' : 'item-name'; name.textContent = item.name;
        if (!inTrash && item.kind === 'FOLDER') {
          row.classList.add('folder-row');
          row.addEventListener('click', event => {
            if (event.target.closest('.row-actions')) return;
            openFolder(item);
          });
          name.type = 'button';
          name.setAttribute('aria-label', `Open folder ${item.name}`);
          icon.setAttribute('role', 'button');
          icon.setAttribute('tabindex', '0');
          icon.setAttribute('aria-label', `Open folder ${item.name}`);
          icon.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openFolder(item); } });
        }
        if (!inTrash && item.kind === 'FILE') {
          row.classList.add('file-row'); row.tabIndex = 0;
          row.setAttribute('aria-label', `Preview ${item.name}`);
          row.addEventListener('click', event => { if (!event.target.closest('.row-actions')) openPreview(item); });
          row.addEventListener('keydown', event => { if ((event.key === 'Enter' || event.key === ' ') && event.target === row) { event.preventDefault(); openPreview(item); } });
        }
        nameWrap.append(icon, name); nameCell.append(nameWrap);
        const typeCell = document.createElement('td'); typeCell.textContent = item.isSharerGroup ? `Shared by ${item.displayName}` : item.kind === 'FOLDER' ? 'Folder' : (item.mediaType || 'File');
        const dateCell = document.createElement('td'); dateCell.textContent = formatDate(inTrash ? item.trashedAt : item.createdAt);
        const sizeCell = document.createElement('td'); sizeCell.textContent = item.isSharerGroup ? `${item.itemCount} ${item.itemCount === 1 ? 'item' : 'items'}` : item.kind === 'FOLDER' ? '—' : formatSize(item.size || 0);
        const actionsCell = document.createElement('td'); const actions = document.createElement('div'); actions.className = 'row-actions';
        if (inTrash) {
          actions.append(actionButton('Restore', '↶', '', () => restoreItem(item)));
          actions.append(actionButton('Delete forever', '×', 'delete', () => permanentlyDeleteItem(item)));
        } else {
          if (item.kind === 'FILE') {
            actions.append(actionButton('Version history', 'History', 'share-action', () => openVersionHistory(item)));
            actions.append(actionButton('Download', '↓', '', () => downloadItem(item)));
          }
          if (item.kind === 'FOLDER') actions.append(actionButton('Open folder', '↗', '', () => openFolder(item)));
          if (['OWNER','EDITOR'].includes(item.accessLevel || 'OWNER')) actions.append(actionButton('Edit', 'Edit', 'share-action', () => openEdit(item)));
          if (item.isSharerGroup) actions.append(actionButton('Open shared items', '↗', '', () => openFolder(item)));
          else if ((item.accessLevel || 'OWNER') === 'OWNER') {
            actions.append(actionButton('Share', 'Share', 'share-action', () => openShare(item)));
            actions.append(actionButton('Move to Trash', '×', 'delete', () => deleteItem(item)));
          }
        }
        actionsCell.append(actions); row.append(nameCell, typeCell, dateCell, sizeCell, actionsCell); itemsEl.append(row);
      }
    }
    async function loadQuota() {
      try {
        const response = await request('/api/quota');
        const quota = await response.json();
        const percent = quota.quotaBytes ? Math.min(100, quota.usedBytes / quota.quotaBytes * 100) : 0;
        document.getElementById('quotaUsed').textContent = `${formatSize(quota.usedBytes)} used`;
        document.getElementById('quotaRemaining').textContent = `${formatSize(quota.availableBytes)} available`;
        document.getElementById('quotaTotal').textContent = `of ${formatSize(quota.quotaBytes)} total`;
        const meter = document.getElementById('quotaMeter');
        meter.style.width = `${percent}%`;
        meter.classList.toggle('near-limit', percent >= 80 && percent < 100);
        meter.classList.toggle('at-limit', percent >= 100);
        meter.parentElement.setAttribute('aria-valuenow', String(Math.round(percent)));
      } catch (_) { document.getElementById('quotaRemaining').textContent = 'Unavailable'; }
    }
    async function loadItems() {
      renderBreadcrumbs();
      loadQuota();
      setStatus('Loading items…');
      const loadRequest = ++state.loadRequest;
      try {
        if (state.view === 'trash') {
          document.getElementById('listToolbar').classList.add('hidden');
          document.getElementById('pagination').classList.add('hidden');
          const response = await request('/api/trash');
          const items = await response.json();
          if (loadRequest !== state.loadRequest) return;
          render(items);
          setStatus(`${items.length} ${items.length === 1 ? 'item' : 'items'} in Trash`);
          return;
        }
        document.getElementById('listToolbar').classList.remove('hidden');
        document.getElementById('pagination').classList.remove('hidden');
        const sharedPeopleRoot = state.view === 'shared' && !state.sharedOwnerId;
        const sharedOwnerRoot = state.view === 'shared' && Boolean(state.sharedOwnerId) && !state.folderId;
        document.getElementById('sortSelect').closest('.sort-controls').classList.toggle('hidden', sharedPeopleRoot);
        document.getElementById('searchInput').placeholder = sharedPeopleRoot ? 'Search people who shared with you' : 'Search this folder';
        const params = new URLSearchParams({page:String(state.page), size:String(state.pageSize), sort:state.sort, direction:state.direction});
        if (state.folderId) params.set('parentId', state.folderId);
        if (state.query.trim()) params.set('q', state.query.trim());
        if (sharedPeopleRoot) {
          const response = await request('/api/shared-with-me/sharers');
          let people = await response.json();
          if (state.query.trim()) {
            const query = state.query.trim().toLowerCase();
            people = people.filter(person => person.email.toLowerCase().includes(query) || person.name.toLowerCase().includes(query));
          }
          if (loadRequest !== state.loadRequest) return;
          const groups = people.map(person => ({id:person.id, name:person.email, displayName:person.name, kind:'FOLDER', isSharerGroup:true, itemCount:person.itemCount, accessLevel:'NONE'}));
          render(groups, groups.length);
          document.getElementById('pagination').classList.add('hidden');
          setStatus(`${groups.length} ${groups.length === 1 ? 'person' : 'people'} shared items with you`);
          return;
        }
        const target = sharedOwnerRoot
          ? `/api/shared-with-me/sharers/${encodeURIComponent(state.sharedOwnerId)}/items?${params.toString()}`
          : `/api/item?${params.toString()}`;
        const response = await request(target);
        const result = await response.json();
        if (loadRequest !== state.loadRequest) return;
        if (result.totalPages > 0 && state.page >= result.totalPages) { state.page = result.totalPages - 1; loadItems(); return; }
        render(result.content, result.totalElements);
        const first = result.totalElements === 0 ? 0 : result.page * result.size + 1;
        const last = Math.min((result.page + 1) * result.size, result.totalElements);
        document.getElementById('pageSummary').textContent = result.totalElements === 0 ? '0 items' : `Showing ${first}–${last} of ${result.totalElements}`;
        document.getElementById('previousPage').disabled = result.page === 0;
        document.getElementById('nextPage').disabled = result.totalPages === 0 || result.page >= result.totalPages - 1;
        document.getElementById('pagination').classList.toggle('hidden', result.totalPages <= 1);
        setStatus(`${result.totalElements} ${result.totalElements === 1 ? 'item' : 'items'} · Synced just now`);
      } catch (error) {
        if (loadRequest !== state.loadRequest) return;
        render([]); setStatus(`Could not connect to Drive: ${error.message}`, 'error');
      }
    }
    function openFolder(item) {
      if (state.view === 'trash' || item.kind !== 'FOLDER') return;
      if (item.isSharerGroup) {
        state.sharedOwnerId = item.id; state.sharedOwnerEmail = item.name; state.folderId = null; state.path = []; state.page = 0;
        state.navigation = state.navigation.slice(0, state.navigationIndex + 1);
        state.navigation.push({folderId:null, path:[], sharedOwnerId:state.sharedOwnerId, sharedOwnerEmail:state.sharedOwnerEmail});
        state.navigationIndex = state.navigation.length - 1;
        loadItems(); return;
      }
      navigateTo(item.id, [...state.path, {name:item.name, id:item.id, accessLevel:item.accessLevel || 'OWNER'}]);
    }
    async function createFolder() {
      if (!['OWNER','EDITOR'].includes(currentFolderAccess())) { notify('You don’t have edit access to create a folder here.'); return; }
      const name = prompt('Name your new folder');
      if (name === null) return;
      if (!name.trim()) { notify('Enter a folder name first.'); return; }
      try {
        const response = await request('/api/folders', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({name:name.trim(), parentId:state.folderId})});
        const folder = await response.json();
        if (state.query) { state.query = ''; document.getElementById('searchInput').value = ''; }
        await navigateTo(folder.id, [...state.path, {name:folder.name, id:folder.id, accessLevel:'OWNER'}]);
        setStatus(`${folder.name} created and opened.`, 'success'); notify('Folder created and opened');
      } catch (error) { setStatus(error.message, 'error'); notify(`Could not create folder: ${error.message}`); }
    }
    async function uploadFile(file) {
      if (!file) return;
      if (!['OWNER','EDITOR'].includes(currentFolderAccess())) { notify('You don’t have edit access to upload here.'); return; }
      const form = new FormData(); form.append('file', file);
      const query = state.folderId ? `?parentId=${encodeURIComponent(state.folderId)}` : '';
      const destination = state.path.length ? ` into ${state.path[state.path.length - 1].name}` : ' to My Drive';
      setStatus(`Uploading ${file.name}${destination}…`);
      try {
        await request(`/api/files${query}`, {method:'POST', body:form});
        state.page = 0;
        if (state.query) { state.query = ''; document.getElementById('searchInput').value = ''; }
        setStatus(`${file.name} uploaded successfully${destination}.`, 'success'); notify('Upload complete'); await loadItems();
      } catch (error) { setStatus(`Upload failed: ${error.message}`, 'error'); notify(`Upload failed: ${error.message}`); }
    }
    async function uploadFiles(files) {
      for (const file of files) await uploadFile(file);
    }
    async function downloadItem(item) {
      try {
        setStatus(`Preparing ${item.name}…`);
        const response = await request(`/api/files/${encodeURIComponent(item.id)}`);
        const blob = await response.blob();
        const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = item.name;
        document.body.append(link); link.click(); link.remove(); URL.revokeObjectURL(link.href);
        setStatus(`${item.name} downloaded.`, 'success');
      } catch (error) { setStatus(`Download failed: ${error.message}`, 'error'); notify(`Download failed: ${error.message}`); }
    }
    function previewKind(item) {
      const type = (item.mediaType || '').toLowerCase().split(';')[0].trim();
      if (['image/png','image/jpeg','image/gif','image/webp','image/avif','image/bmp'].includes(type)) return 'image';
      if (type === 'application/pdf') return 'pdf';
      if (type.startsWith('audio/')) return 'audio';
      if (type.startsWith('video/')) return 'video';
      return null;
    }
    let activePreviewItem = null;
    let activePdfViewer = null;
    let pdfRenderGeneration = 0;
    async function openPreview(item) {
      activePreviewItem = item;
      const dialog = document.getElementById('previewDialog');
      const body = document.getElementById('previewBody');
      disposePdfViewer();
      document.getElementById('previewTitle').textContent = item.name;
      document.getElementById('previewDetails').textContent = `${item.mediaType || 'Unknown file type'} · ${formatSize(item.size || 0)}`;
      const kind = previewKind(item);
      document.getElementById('previewFullscreen').classList.toggle('hidden', !kind || kind === 'audio');
      body.replaceChildren();
      dialog.showModal();
      if (!kind) {
        body.innerHTML = '<div class="preview-message"><strong>Preview unavailable</strong>This file type cannot be displayed in the browser. You can still download it.</div>';
        return;
      }
      body.innerHTML = '<div class="preview-message">Preparing secure preview…</div>';
      try {
        const response = await request(`/api/files/${encodeURIComponent(item.id)}/preview-ticket`, {method:'POST'});
        const result = await response.json();
        if (activePreviewItem?.id !== item.id || !dialog.open) return;
        const source = result.url;
        if (kind === 'pdf') { await buildPdfPreview(body, source); return; }
        let media;
        if (kind === 'image') { media = document.createElement('img'); media.alt = item.name; media.src = source; }
        else if (kind === 'audio') { media = document.createElement('audio'); media.controls = true; media.setAttribute('controlsList','nodownload'); media.preload = 'metadata'; media.src = source; }
        else { media = document.createElement('video'); media.controls = true; media.setAttribute('controlsList','nodownload nofullscreen'); media.playsInline = true; media.preload = 'metadata'; media.src = source; }
        body.replaceChildren(media);
      } catch (error) {
        if (activePreviewItem?.id !== item.id || !dialog.open) return;
        const message = document.createElement('div'); message.className = 'preview-message';
        const title = document.createElement('strong'); title.textContent = 'Could not open preview';
        message.append(title, document.createTextNode(error.message)); body.replaceChildren(message);
      }
    }
    function disposePdfViewer() {
      const viewer = activePdfViewer;
      activePdfViewer = null;
      pdfRenderGeneration++;
      if (viewer?.renderTask) { try { viewer.renderTask.cancel(); } catch (_) { } }
      if (viewer?.observer) viewer.observer.disconnect();
      if (viewer?.renderTasks) for (const task of viewer.renderTasks.values()) { try { task.cancel(); } catch (_) { } }
      if (viewer?.loadingTask) viewer.loadingTask.destroy().catch(() => {});
    }
    async function buildPdfPreview(container, source) {
      const viewer = {loadingTask:null, document:null, renderTasks:new Map(), rendered:new Set(), page:1, scale:1, pages:[], observer:null};
      activePdfViewer = viewer;
      const shell = document.createElement('div'); shell.className = 'pdf-preview';
      const toolbar = document.createElement('div'); toolbar.className = 'pdf-toolbar'; toolbar.setAttribute('aria-label','PDF controls');
      const previous = document.createElement('button'); previous.type='button'; previous.className='pdf-tool'; previous.textContent='‹'; previous.title='Previous page'; previous.setAttribute('aria-label','Previous page');
      const pageInput = document.createElement('input'); pageInput.className='pdf-page-number'; pageInput.type='number'; pageInput.min='1'; pageInput.value='1'; pageInput.setAttribute('aria-label','Page number');
      const pageCount = document.createElement('span'); pageCount.className='pdf-zoom-label'; pageCount.textContent='of —';
      const next = document.createElement('button'); next.type='button'; next.className='pdf-tool'; next.textContent='›'; next.title='Next page'; next.setAttribute('aria-label','Next page');
      const separator = document.createElement('span'); separator.className='pdf-separator'; separator.setAttribute('aria-hidden','true');
      const zoomOut = document.createElement('button'); zoomOut.type='button'; zoomOut.className='pdf-tool'; zoomOut.textContent='−'; zoomOut.title='Zoom out'; zoomOut.setAttribute('aria-label','Zoom out');
      const zoomLabel = document.createElement('span'); zoomLabel.className='pdf-zoom-label'; zoomLabel.textContent='100%';
      const zoomIn = document.createElement('button'); zoomIn.type='button'; zoomIn.className='pdf-tool'; zoomIn.textContent='+'; zoomIn.title='Zoom in'; zoomIn.setAttribute('aria-label','Zoom in');
      toolbar.append(previous, pageInput, pageCount, next, separator, zoomOut, zoomLabel, zoomIn);
      const stage = document.createElement('div'); stage.className='pdf-page-stage';
      const loadMessage = document.createElement('div'); loadMessage.className='pdf-load-message'; loadMessage.textContent='Loading PDF…';
      const pageStack = document.createElement('div'); pageStack.className='pdf-page-stack hidden';
      stage.append(loadMessage, pageStack); shell.append(toolbar, stage); container.replaceChildren(shell);

      const setControls = () => {
        pageInput.value = String(viewer.page);
        pageCount.textContent = `of ${viewer.document.numPages}`;
        previous.disabled = viewer.page <= 1; next.disabled = viewer.page >= viewer.document.numPages;
        zoomLabel.textContent = `${Math.round(viewer.scale * 100)}%`;
      };
      const goToPage = pageNumber => {
        viewer.page = Math.max(1, Math.min(viewer.document.numPages, pageNumber));
        setControls();
        viewer.pages[viewer.page - 1]?.element.scrollIntoView({block:'start', behavior:'smooth'});
      };
      const renderPage = async pageInfo => {
        if (activePdfViewer !== viewer || viewer.rendered.has(pageInfo.number) || viewer.renderTasks.has(pageInfo.number)) return;
        const generation = pdfRenderGeneration;
        const viewport = pageInfo.page.getViewport({scale:viewer.scale});
        const outputScale = window.devicePixelRatio || 1;
        pageInfo.canvas.width = Math.floor(viewport.width * outputScale); pageInfo.canvas.height = Math.floor(viewport.height * outputScale);
        pageInfo.canvas.style.width = `${Math.floor(viewport.width)}px`; pageInfo.canvas.style.height = `${Math.floor(viewport.height)}px`;
        pageInfo.element.style.width = `${Math.floor(viewport.width)}px`; pageInfo.element.style.height = `${Math.floor(viewport.height)}px`;
        const task = pageInfo.page.render({canvasContext:pageInfo.canvas.getContext('2d'), viewport, transform:outputScale === 1 ? null : [outputScale,0,0,outputScale,0,0]});
        viewer.renderTasks.set(pageInfo.number, task);
        try { await task.promise; if (activePdfViewer === viewer && generation === pdfRenderGeneration) viewer.rendered.add(pageInfo.number); }
        catch (error) { if (error?.name !== 'RenderingCancelledException') throw error; }
        finally { if (viewer.renderTasks.get(pageInfo.number) === task) viewer.renderTasks.delete(pageInfo.number); }
      };
      const onStageScroll = () => {
        if (!viewer.pages.length) return;
        const stageRect = stage.getBoundingClientRect();
        const focusLine = stageRect.top + Math.min(120, stage.clientHeight * .2);
        let visiblePage = viewer.pages[0];
        for (const pageInfo of viewer.pages) {
          const rect = pageInfo.element.getBoundingClientRect();
          if (rect.top <= focusLine && rect.bottom > focusLine) { visiblePage = pageInfo; break; }
          if (rect.top <= focusLine) visiblePage = pageInfo;
        }
        if (visiblePage.number !== viewer.page) { viewer.page = visiblePage.number; setControls(); }
      };
      const applyZoom = async () => {
        viewer.scale = Math.max(.5, Math.min(2.5, Math.round(viewer.scale * 10) / 10));
        const generation = ++pdfRenderGeneration;
        const anchorPage = viewer.pages[viewer.page - 1];
        const anchorOffset = anchorPage ? anchorPage.element.getBoundingClientRect().top - stage.getBoundingClientRect().top : 0;
        const pendingTasks = [...viewer.renderTasks.values()];
        for (const task of pendingTasks) { try { task.cancel(); } catch (_) { } }
        await Promise.all(pendingTasks.map(task => task.promise.catch(() => {})));
        viewer.renderTasks.clear(); viewer.rendered.clear();
        for (const pageInfo of viewer.pages) {
          const viewport = pageInfo.page.getViewport({scale:viewer.scale});
          pageInfo.element.style.width = `${Math.floor(viewport.width)}px`; pageInfo.element.style.height = `${Math.floor(viewport.height)}px`;
          pageInfo.canvas.width = 0; pageInfo.canvas.height = 0;
        }
        setControls();
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        if (activePdfViewer !== viewer || generation !== pdfRenderGeneration) return;
        if (anchorPage) {
          const newOffset = anchorPage.element.getBoundingClientRect().top - stage.getBoundingClientRect().top;
          stage.scrollTop = Math.max(0, stage.scrollTop + newOffset - anchorOffset);
        }
        for (const pageInfo of viewer.pages) { viewer.observer.unobserve(pageInfo.element); viewer.observer.observe(pageInfo.element); }
        if (anchorPage) await renderPage(anchorPage);
      };
      previous.addEventListener('click', () => goToPage(viewer.page - 1));
      next.addEventListener('click', () => goToPage(viewer.page + 1));
      pageInput.addEventListener('change', () => goToPage(Number(pageInput.value) || 1));
      zoomOut.addEventListener('click', () => { viewer.scale -= .1; applyZoom().catch(showError); });
      zoomIn.addEventListener('click', () => { viewer.scale += .1; applyZoom().catch(showError); });
      stage.addEventListener('scroll', onStageScroll, {passive:true});
      const showError = error => { if (activePdfViewer !== viewer) return; loadMessage.classList.remove('hidden'); loadMessage.classList.add('pdf-error'); loadMessage.textContent=`Could not display this PDF: ${error.message || 'unsupported or damaged document'}`; pageStack.classList.add('hidden'); };
      try {
        const pdfjs = await import('/vendor/pdfjs/pdf.min.mjs');
        if (activePdfViewer !== viewer) return;
        pdfjs.GlobalWorkerOptions.workerSrc='/vendor/pdfjs/pdf.worker.min.mjs';
        viewer.loadingTask = pdfjs.getDocument({url:source, cMapUrl:'/vendor/pdfjs/cmaps/', cMapPacked:true, standardFontDataUrl:'/vendor/pdfjs/standard_fonts/', wasmUrl:'/vendor/pdfjs/wasm/'});
        viewer.document = await viewer.loadingTask.promise;
        if (activePdfViewer !== viewer) return;
        pageInput.max=String(viewer.document.numPages); setControls();
        const observer = new IntersectionObserver(entries => {
          for (const entry of entries) if (entry.isIntersecting) {
            const pageInfo = viewer.pages.find(candidate => candidate.element === entry.target);
            if (pageInfo) renderPage(pageInfo).catch(showError);
          }
        }, {root:stage, rootMargin:'1000px 0px', threshold:0});
        viewer.observer = observer;
        for (let number=1; number<=viewer.document.numPages; number++) {
          const page = await viewer.document.getPage(number);
          const viewport = page.getViewport({scale:viewer.scale});
          const element = document.createElement('div'); element.className='pdf-canvas-wrap';
          element.style.width=`${Math.floor(viewport.width)}px`; element.style.height=`${Math.floor(viewport.height)}px`;
          const pageCanvas = document.createElement('canvas'); element.append(pageCanvas); pageStack.append(element);
          const pageInfo = {number, page, element, canvas:pageCanvas}; viewer.pages.push(pageInfo); observer.observe(element);
        }
        loadMessage.classList.add('hidden'); pageStack.classList.remove('hidden');
        setControls(); onStageScroll();
      } catch (error) { showError(error); }
    }
    function closePreview() {
      const dialog = document.getElementById('previewDialog');
      document.getElementById('previewBody').querySelectorAll('audio,video').forEach(media => { media.pause(); media.removeAttribute('src'); media.load(); });
      document.getElementById('previewBody').replaceChildren(); activePreviewItem = null;
      if (dialog.open) dialog.close();
    }
    document.getElementById('previewClose').addEventListener('click', closePreview);
    document.getElementById('previewDialog').addEventListener('click', event => { if (event.target === event.currentTarget) closePreview(); });
    document.getElementById('previewDialog').addEventListener('close', () => {
      setPreviewFullscreen(false);
      document.getElementById('previewBody').querySelectorAll('audio,video').forEach(media => { media.pause(); media.removeAttribute('src'); media.load(); });
      activePreviewItem = null; document.getElementById('previewBody').replaceChildren();
    });
    document.getElementById('previewDownload').addEventListener('click', () => { if (activePreviewItem) downloadItem(activePreviewItem); });
    function setPreviewFullscreen(enabled) {
      const dialog = document.getElementById('previewDialog');
      const button = document.getElementById('previewFullscreen');
      dialog.classList.toggle('is-fullscreen', enabled);
      button.setAttribute('aria-pressed', String(enabled));
      button.setAttribute('aria-label', enabled ? 'Exit full screen' : 'Full screen');
      button.title = enabled ? 'Exit full screen' : 'Full screen';
      button.innerHTML = enabled
        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 3v3a2 2 0 0 1-2 2H3m13-5v3a2 2 0 0 0 2 2h3M3 16h3a2 2 0 0 1 2 2v3m13-5h-3a2 2 0 0 0-2 2v3"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 3H5a2 2 0 0 0-2 2v3m13-5h3a2 2 0 0 1 2 2v3M3 16v3a2 2 0 0 0 2 2h3m13-5v3a2 2 0 0 1-2 2h-3"/></svg>';
      requestAnimationFrame(() => document.getElementById('previewBody').dispatchEvent(new Event('resize')));
    }
    document.getElementById('previewFullscreen').addEventListener('click', () => {
      setPreviewFullscreen(!document.getElementById('previewDialog').classList.contains('is-fullscreen'));
    });
    document.getElementById('previewDialog').addEventListener('cancel', event => {
      if (event.currentTarget.classList.contains('is-fullscreen')) { event.preventDefault(); setPreviewFullscreen(false); }
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && document.getElementById('previewDialog').classList.contains('is-fullscreen')) {
        event.preventDefault(); setPreviewFullscreen(false);
      }
    });
    async function deleteItem(item) {
      if (!confirm(`Move “${item.name}” to Trash?`)) return;
      try {
        await request(`/api/item/${encodeURIComponent(item.id)}`, {method:'DELETE'});
        setStatus(`${item.name} moved to Trash.`, 'success'); notify('Moved to Trash'); await loadItems();
      } catch (error) { setStatus(`Could not move ${item.name} to Trash: ${error.message}`, 'error'); notify(`Could not move to Trash: ${error.message}`); }
    }
    async function restoreItem(item) {
      try {
        await request(`/api/trash/${encodeURIComponent(item.id)}/restore`, {method:'POST'});
        setStatus(`${item.name} restored.`, 'success'); notify('Item restored'); await loadItems();
      } catch (error) { setStatus(`Could not restore ${item.name}: ${error.message}`, 'error'); notify(`Restore failed: ${error.message}`); }
    }
    async function permanentlyDeleteItem(item) {
      if (!confirm(`Permanently delete “${item.name}”? This cannot be undone.`)) return;
      try {
        await request(`/api/trash/${encodeURIComponent(item.id)}`, {method:'DELETE'});
        setStatus(`${item.name} permanently deleted.`, 'success'); notify('Permanently deleted'); await loadItems();
      } catch (error) { setStatus(`Could not permanently delete ${item.name}: ${error.message}`, 'error'); notify(`Delete failed: ${error.message}`); }
    }
    let activeShareItem = null;
    let activeEditItem = null;
    function openEdit(item) {
      if (!['OWNER','EDITOR'].includes(item.accessLevel || 'OWNER')) return;
      activeEditItem = item;
      document.getElementById('editItemName').textContent = item.name;
      document.getElementById('editName').value = item.name;
      document.getElementById('replacementFile').value = '';
      document.getElementById('replacementField').classList.toggle('hidden', item.kind !== 'FILE');
      document.getElementById('editStatus').textContent = '';
      document.getElementById('editStatus').className = 'share-status';
      document.getElementById('editDialog').showModal();
    }
    document.getElementById('replacementFile').addEventListener('change', event => {
      const replacement = event.currentTarget.files[0];
      if (replacement) document.getElementById('editName').value = replacement.name;
    });
    document.getElementById('editForm').addEventListener('submit', async event => {
      event.preventDefault(); if (!activeEditItem) return;
      const status = document.getElementById('editStatus'); status.className = 'share-status'; status.textContent = 'Saving changes…';
      const data = new FormData(); data.append('name', document.getElementById('editName').value.trim());
      const replacement = document.getElementById('replacementFile').files[0];
      if (replacement) data.append('file', replacement);
      const editedItem = activeEditItem;
      try {
        await request(`/api/item/${encodeURIComponent(editedItem.id)}`, {method:'PUT', body:data});
        document.getElementById('editDialog').close();
        setStatus(`${editedItem.name} updated.`, 'success'); notify('Changes saved'); await loadItems();
      } catch (error) { status.textContent = error.message; status.classList.add('error'); }
    });
    document.getElementById('editClose').addEventListener('click', () => document.getElementById('editDialog').close());
    document.getElementById('editDialog').addEventListener('close', () => { activeEditItem = null; });
    let activeVersionItem = null;
    async function openVersionHistory(item) {
      activeVersionItem = item;
      document.getElementById('versionsFileName').textContent = item.name;
      const status = document.getElementById('versionsStatus');
      status.className = 'share-status'; status.textContent = 'Loading versions…';
      document.getElementById('versionsList').replaceChildren();
      document.getElementById('versionsDialog').showModal();
      try {
        const response = await request(`/api/files/${encodeURIComponent(item.id)}/versions`);
        const versions = await response.json();
        status.textContent = versions.length ? 'Earlier saved versions' : 'No earlier versions yet. Replace this file to start its history.';
        const list = document.getElementById('versionsList');
        for (const version of versions) {
          const row = document.createElement('div'); row.className = 'share-entry';
          const details = document.createElement('div'); details.className = 'share-person';
          const name = document.createElement('strong'); name.textContent = `Version ${version.revisionNumber} · ${version.name}`;
          const info = document.createElement('span'); info.textContent = `${formatDate(version.createdAt)} · ${formatSize(version.size)}${version.mediaType ? ` · ${version.mediaType}` : ''}`;
          details.append(name, info); row.append(details);
          if (['OWNER','EDITOR'].includes(item.accessLevel || 'OWNER')) {
            row.append(actionButton('Restore this version', 'Restore', 'share-action', () => restoreFileVersion(item, version)));
          }
          list.append(row);
        }
      } catch (error) { status.textContent = error.message; status.classList.add('error'); }
    }
    async function restoreFileVersion(item, version) {
      if (!confirm(`Restore version ${version.revisionNumber} of “${item.name}”? The current contents will be saved in history first.`)) return;
      const status = document.getElementById('versionsStatus'); status.className = 'share-status'; status.textContent = 'Restoring version…';
      try {
        const response = await request(`/api/files/${encodeURIComponent(item.id)}/versions/${encodeURIComponent(version.id)}/restore`, {method:'POST'});
        const restored = await response.json();
        document.getElementById('versionsDialog').close();
        setStatus(`${restored.name} restored from version ${version.revisionNumber}.`, 'success'); notify('Earlier version restored');
        await loadItems();
      } catch (error) { status.textContent = error.message; status.classList.add('error'); }
    }
    document.getElementById('versionsClose').addEventListener('click', () => document.getElementById('versionsDialog').close());
    document.getElementById('versionsDialog').addEventListener('close', () => { activeVersionItem = null; });
    async function openShare(item) {
      if (item.accessLevel && item.accessLevel !== 'OWNER') return;
      activeShareItem = item;
      document.getElementById('shareItemName').textContent = item.name;
      document.getElementById('shareEmail').value = '';
      document.getElementById('shareStatus').textContent = '';
      document.getElementById('shareStatus').className = 'share-status';
      document.getElementById('shareDialog').showModal();
      await loadShares();
    }
    async function loadShares() {
      if (!activeShareItem) return;
      const list = document.getElementById('shareList'); list.replaceChildren();
      try {
        const response = await request(`/api/items/${encodeURIComponent(activeShareItem.id)}/shares`);
        const entries = await response.json();
        if (!entries.length) { const empty = document.createElement('p'); empty.className = 'share-status'; empty.textContent = 'This item is not shared yet.'; list.append(empty); return; }
        for (const entry of entries) {
          const row = document.createElement('div'); row.className = 'share-entry';
          const person = document.createElement('div'); person.className = 'share-person';
          const name = document.createElement('strong'); name.textContent = entry.name;
          const email = document.createElement('span'); email.textContent = entry.email;
          person.append(name, email);
          const role = document.createElement('select'); role.setAttribute('aria-label', `Access for ${entry.email}`);
          for (const [value, label] of [['VIEWER','Viewer'],['EDITOR','Editor']]) { const option = document.createElement('option'); option.value = value; option.textContent = label; role.append(option); }
          role.value = entry.permission;
          role.addEventListener('change', async () => {
            try { await request(`/api/items/${encodeURIComponent(activeShareItem.id)}/shares`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:entry.email,permission:role.value})}); notify('Access updated'); }
            catch (error) { role.value = entry.permission; document.getElementById('shareStatus').textContent = error.message; document.getElementById('shareStatus').classList.add('error'); }
          });
          const revoke = document.createElement('button'); revoke.type='button'; revoke.className='share-revoke'; revoke.textContent='Remove';
          revoke.addEventListener('click', async () => {
            try { await request(`/api/items/${encodeURIComponent(activeShareItem.id)}/shares/${encodeURIComponent(entry.userId)}`, {method:'DELETE'}); await loadShares(); notify('Access removed'); }
            catch (error) { document.getElementById('shareStatus').textContent = error.message; document.getElementById('shareStatus').classList.add('error'); }
          });
          row.append(person, role, revoke); list.append(row);
        }
      } catch (error) { document.getElementById('shareStatus').textContent = error.message; document.getElementById('shareStatus').classList.add('error'); }
    }
    document.getElementById('shareForm').addEventListener('submit', async event => {
      event.preventDefault(); if (!activeShareItem) return;
      const status = document.getElementById('shareStatus'); status.className='share-status'; status.textContent='Sharing…';
      try {
        await request(`/api/items/${encodeURIComponent(activeShareItem.id)}/shares`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:document.getElementById('shareEmail').value.trim(),permission:document.getElementById('sharePermission').value})});
        document.getElementById('shareEmail').value=''; status.textContent='Access granted.'; await loadShares(); notify('Item shared');
      } catch (error) { status.textContent=error.message; status.classList.add('error'); }
    });
    document.getElementById('shareClose').addEventListener('click', () => document.getElementById('shareDialog').close());
    document.getElementById('shareDialog').addEventListener('close', () => { activeShareItem=null; });
    function switchView(view) {
      state.view = view; state.folderId = null; state.path = []; state.sharedOwnerId = null; state.sharedOwnerEmail = null; state.page = 0; state.query = '';
      document.getElementById('searchInput').value = '';
      state.navigation = [{folderId:null,path:[],sharedOwnerId:null,sharedOwnerEmail:null}]; state.navigationIndex = 0;
      const trash = view === 'trash'; const shared = view === 'shared';
      document.getElementById('trashButton').textContent = 'Trash';
      document.getElementById('sharedButton').textContent = 'Shared with me';
      document.getElementById('searchInput').disabled = trash;
      document.getElementById('myDriveButton').classList.toggle('active', view === 'drive');
      document.getElementById('sharedButton').classList.toggle('active', shared);
      document.getElementById('trashButton').classList.toggle('active', trash);
      document.getElementById('pageTitle').textContent = trash ? 'Trash' : shared ? 'Shared with me' : 'My Drive';
      document.getElementById('pageDescription').textContent = trash ? 'Restore items or delete them forever.' : shared ? 'Files and folders other people shared with you.' : 'Keep your files organized and easy to find.';
      loadItems();
    }
    document.getElementById('trashButton').addEventListener('click', () => { document.getElementById('appLayout').classList.remove('sidebar-open'); switchView(state.view === 'trash' ? 'drive' : 'trash'); });
    document.getElementById('sharedButton').addEventListener('click', () => { document.getElementById('appLayout').classList.remove('sidebar-open'); switchView(state.view === 'shared' ? 'drive' : 'shared'); });
    document.getElementById('newFolder').addEventListener('click', () => { document.getElementById('appLayout').classList.remove('sidebar-open'); createFolder(); });
    document.getElementById('uploadButton').addEventListener('click', () => document.getElementById('fileInput').click());
    document.getElementById('emptyUpload').addEventListener('click', () => document.getElementById('fileInput').click());
    document.getElementById('fileInput').addEventListener('change', event => {
      uploadFiles(Array.from(event.target.files)); event.target.value = '';
    });
    document.getElementById('refresh').addEventListener('click', loadItems);
    document.getElementById('listView').addEventListener('click', () => {
      state.layout = 'list';
      document.getElementById('listView').classList.add('selected');
      document.getElementById('listView').setAttribute('aria-pressed', 'true');
      document.getElementById('gridView').classList.remove('selected');
      document.getElementById('gridView').setAttribute('aria-pressed', 'false');
      renderLastItems();
    });
    document.getElementById('gridView').addEventListener('click', () => {
      state.layout = 'grid';
      document.getElementById('gridView').classList.add('selected');
      document.getElementById('gridView').setAttribute('aria-pressed', 'true');
      document.getElementById('listView').classList.remove('selected');
      document.getElementById('listView').setAttribute('aria-pressed', 'false');
      renderLastItems();
    });
    document.getElementById('mobileMenu').addEventListener('click', () => document.getElementById('appLayout').classList.toggle('sidebar-open'));
    document.getElementById('appMain').addEventListener('click', () => document.getElementById('appLayout').classList.remove('sidebar-open'));
    document.getElementById('myDriveButton').addEventListener('click', () => { document.getElementById('appLayout').classList.remove('sidebar-open'); switchView('drive'); });
    document.getElementById('backFolder').addEventListener('click', () => moveInNavigation(-1));
    document.getElementById('forwardFolder').addEventListener('click', () => moveInNavigation(1));
    document.getElementById('upFolder').addEventListener('click', () => {
      if (state.view === 'shared' && state.sharedOwnerId && !state.path.length) { switchView('shared'); return; }
      if (!state.path.length) return;
      const parentPath = state.path.slice(0, -1);
      const parentId = parentPath.length ? parentPath[parentPath.length - 1].id : null;
      navigateTo(parentId, parentPath);
    });
    const drivePanel = document.getElementById('drivePanel');
    drivePanel.addEventListener('dragover', event => {
      if (!['OWNER','EDITOR'].includes(currentFolderAccess()) || !Array.from(event.dataTransfer.types).includes('Files')) return;
      event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; drivePanel.classList.add('drag-over');
    });
    drivePanel.addEventListener('dragenter', event => {
      if (['OWNER','EDITOR'].includes(currentFolderAccess()) && Array.from(event.dataTransfer.types).includes('Files')) { event.preventDefault(); drivePanel.classList.add('drag-over'); }
    });
    drivePanel.addEventListener('dragleave', event => {
      if (!drivePanel.contains(event.relatedTarget)) drivePanel.classList.remove('drag-over');
    });
    drivePanel.addEventListener('drop', event => {
      event.preventDefault(); drivePanel.classList.remove('drag-over');
      if (['OWNER','EDITOR'].includes(currentFolderAccess()) && event.dataTransfer.files.length) uploadFiles(Array.from(event.dataTransfer.files));
    });
    let searchTimer;
    document.getElementById('searchInput').addEventListener('input', event => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => { state.query = event.target.value; state.page = 0; loadItems(); }, 250);
    });
    document.getElementById('sortSelect').addEventListener('change', event => { state.sort = event.target.value; state.page = 0; loadItems(); });
    document.getElementById('sortDirection').addEventListener('click', event => {
      state.direction = state.direction === 'asc' ? 'desc' : 'asc';
      event.currentTarget.textContent = state.direction === 'asc' ? '↑' : '↓';
      event.currentTarget.setAttribute('aria-label', `Sort ${state.direction === 'asc' ? 'ascending' : 'descending'}`);
      event.currentTarget.title = `Sort ${state.direction === 'asc' ? 'ascending' : 'descending'}`;
      state.page = 0; loadItems();
    });
    document.getElementById('previousPage').addEventListener('click', () => { if (state.page > 0) { state.page--; loadItems(); } });
    document.getElementById('nextPage').addEventListener('click', () => { state.page++; loadItems(); });
    function showDrive() {
      const signedIn = Boolean(state.token);
      document.getElementById('authCard').classList.toggle('hidden', signedIn);
      document.getElementById('account').classList.toggle('hidden', !signedIn);
      document.querySelectorAll('.requires-auth').forEach(element => element.classList.toggle('hidden', !signedIn));
      if (signedIn) {
        document.getElementById('accountEmail').textContent = localStorage.getItem('driveEmail') || 'Signed in';
        loadItems();
      }
    }
    function setAuthMode(mode) {
      state.authMode = mode;
      const registering = mode === 'register';
      document.getElementById('authTitle').textContent = registering ? 'Create your Drive account' : 'Sign in to Drive';
      document.getElementById('authDescription').textContent = registering ? 'Create an account to keep your files private.' : 'Your files are private to your account.';
      document.getElementById('nameField').classList.toggle('hidden', !registering);
      document.getElementById('authName').required = registering;
      document.getElementById('authPassword').autocomplete = registering ? 'new-password' : 'current-password';
      document.getElementById('authSubmit').textContent = registering ? 'Create account' : 'Sign in';
      document.getElementById('authSwitch').textContent = registering ? 'Already have an account? Sign in' : 'Create an account';
      document.getElementById('authError').classList.add('hidden');
    }
    document.getElementById('authSwitch').addEventListener('click', () => setAuthMode(state.authMode === 'login' ? 'register' : 'login'));
    document.getElementById('authForm').addEventListener('submit', async event => {
      event.preventDefault();
      const errorEl = document.getElementById('authError'); errorEl.classList.add('hidden');
      const form = new FormData(event.currentTarget);
      const payload = {email:form.get('email'), password:form.get('password')};
      if (state.authMode === 'register') payload.name = form.get('name');
      try {
        const response = await request(`/api/auth/${state.authMode === 'register' ? 'register' : 'login'}`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
        const result = await response.json(); state.token = result.accessToken;
        localStorage.setItem('driveAccessToken', result.accessToken); localStorage.setItem('driveEmail', result.email);
        showDrive();
      } catch (error) { errorEl.textContent = error.message; errorEl.classList.remove('hidden'); errorEl.classList.add('error'); }
    });
    document.getElementById('logout').addEventListener('click', async () => {
      try { await request('/api/auth/logout', {method:'POST'}); } catch (_) { }
      state.token = null; state.folderId = null; state.path = [];
      localStorage.removeItem('driveAccessToken'); localStorage.removeItem('driveEmail');
      setAuthMode('login'); showDrive();
    });
    setAuthMode('login');
    document.getElementById('listView').classList.add('selected');
    showDrive();
