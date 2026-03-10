(function () {
    const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
    let googleClientId = (import.meta.env.VITE_GOOGLE_CLIENT_ID || "").trim();
    let googleScriptPromise = null;
    let authConfigPromise = null;
    const MAIL_PROVIDERS = {
        custom: "CUSTOM",
        gmail: "GMAIL_APP_PASSWORD"
    };
    const GMAIL_PRESET = {
        imapHost: "imap.gmail.com",
        imapPort: 993,
        imapSsl: true,
        smtpHost: "smtp.gmail.com",
        smtpPort: 465,
        smtpStarttls: false,
        smtpSsl: true
    };

    const STORAGE_KEYS = {
        access: "mailing.accessToken",
        refresh: "mailing.refreshToken"
    };

    const state = {
        accessToken: null,
        refreshToken: null,
        folder: "INBOX",
        folders: [],
        messages: [],
        searchQuery: "",
        searchDebounceTimer: null,
        selectedUid: null,
        selectedDetail: null,
        selectedThread: null,
        selectedThreadMessageKey: null,
        total: 0,
        offset: 0,
        limit: 10,
        backendChunkSize: 30,
        unseenOnly: false,
        pendingRequests: 0,
        messagePageCache: new Map(),
        messagePageReqId: 0,
        composeReplyContext: null,
        readerMode: false,
        mailSettingsConfigured: false,
        settingsLocked: false
    };

    const el = {
        loginView: document.getElementById("loginView"),
        appView: document.getElementById("appView"),
        loginForm: document.getElementById("loginForm"),
        loginUsername: document.getElementById("loginUsername"),
        loginPassword: document.getElementById("loginPassword"),
        googleSigninButton: document.getElementById("googleSigninButton"),
        googleSigninHint: document.getElementById("googleSigninHint"),
        sessionMeta: document.getElementById("sessionMeta"),
        folders: document.getElementById("folders"),
        messages: document.getElementById("messages"),
        detail: document.getElementById("detail"),
        messageMeta: document.getElementById("messageMeta"),
        mailListCard: document.getElementById("mailListCard"),
        detailCard: document.getElementById("detailCard"),
        btnBackToList: document.getElementById("btnBackToList"),
        searchInput: document.getElementById("searchInput"),
        unseenOnly: document.getElementById("unseenOnly"),
        btnPrevPage: document.getElementById("btnPrevPage"),
        btnNextPage: document.getElementById("btnNextPage"),
        btnReloadFolders: document.getElementById("btnReloadFolders"),
        btnMailSettings: document.getElementById("btnMailSettings"),
        btnTestConnection: document.getElementById("btnTestConnection"),
        btnRefreshToken: document.getElementById("btnRefreshToken"),
        btnLogout: document.getElementById("btnLogout"),
        btnComposeOpen: document.getElementById("btnComposeOpen"),
        btnComposeClose: document.getElementById("btnComposeClose"),
        composePanel: document.getElementById("composePanel"),
        composeForm: document.getElementById("composeForm"),
        toInput: document.getElementById("toInput"),
        subjectInput: document.getElementById("subjectInput"),
        textBodyInput: document.getElementById("textBodyInput"),
        attachmentsInput: document.getElementById("attachmentsInput"),
        composeModeMeta: document.getElementById("composeModeMeta"),
        btnMarkRead: document.getElementById("btnMarkRead"),
        btnMarkUnread: document.getElementById("btnMarkUnread"),
        btnReply: document.getElementById("btnReply"),
        btnMove: document.getElementById("btnMove"),
        btnDelete: document.getElementById("btnDelete"),
        settingsPanel: document.getElementById("settingsPanel"),
        settingsForm: document.getElementById("settingsForm"),
        btnSettingsClose: document.getElementById("btnSettingsClose"),
        settingsHint: document.getElementById("settingsHint"),
        settingsProvider: document.getElementById("settingsProvider"),
        settingsProviderHint: document.getElementById("settingsProviderHint"),
        settingsUsername: document.getElementById("settingsUsername"),
        settingsPassword: document.getElementById("settingsPassword"),
        settingsPasswordLabel: document.getElementById("settingsPasswordLabel"),
        settingsFromEmail: document.getElementById("settingsFromEmail"),
        settingsDefaultFolder: document.getElementById("settingsDefaultFolder"),
        settingsImapHost: document.getElementById("settingsImapHost"),
        settingsImapPort: document.getElementById("settingsImapPort"),
        settingsImapSsl: document.getElementById("settingsImapSsl"),
        settingsSmtpHost: document.getElementById("settingsSmtpHost"),
        settingsSmtpPort: document.getElementById("settingsSmtpPort"),
        settingsSmtpStarttls: document.getElementById("settingsSmtpStarttls"),
        settingsSmtpSsl: document.getElementById("settingsSmtpSsl"),
        settingsTimeoutSeconds: document.getElementById("settingsTimeoutSeconds"),
        toast: document.getElementById("toast"),
        globalLoader: document.getElementById("globalLoader")
    };

    boot();

    function boot() {
        restoreSession();
        bindEvents();
        if (state.accessToken && state.refreshToken) {
            showApp();
            loadWorkspace().catch((err) => {
                showToast(err.message, "error");
                if (err && err.status === 401) {
                    logout(true);
                }
            });
        } else {
            showLogin();
        }
    }

    function bindEvents() {
        el.loginForm.addEventListener("submit", onLoginSubmit);
        el.btnReloadFolders.addEventListener("click", () => loadFolders());
        el.btnMailSettings.addEventListener("click", () => openSettingsPanel(false));
        el.searchInput.addEventListener("input", () => {
            state.searchQuery = (el.searchInput.value || "").trim();
            state.offset = 0;
            clearMessageCache();
            window.clearTimeout(state.searchDebounceTimer);
            state.searchDebounceTimer = window.setTimeout(() => {
                loadMessages().catch((err) => showToast(err.message, "error"));
            }, 220);
        });
        el.unseenOnly.addEventListener("change", () => {
            state.unseenOnly = el.unseenOnly.checked;
            state.offset = 0;
            clearMessageCache();
            loadMessages();
        });
        el.btnPrevPage.addEventListener("click", () => {
            state.offset = Math.max(0, state.offset - state.limit);
            loadMessages();
        });
        el.btnNextPage.addEventListener("click", () => {
            if (state.offset + state.limit < state.total) {
                state.offset += state.limit;
                loadMessages();
            }
        });
        el.btnTestConnection.addEventListener("click", testConnection);
        el.btnRefreshToken.addEventListener("click", async () => {
            try {
                await refreshAccessToken(false);
                showToast("Token refreshed.", "success");
            } catch (err) {
                showToast(err.message, "error");
            }
        });
        el.btnLogout.addEventListener("click", () => logout(false));
        el.btnComposeOpen.addEventListener("click", openComposeForNewMessage);
        el.btnComposeClose.addEventListener("click", closeComposePanel);
        el.composeForm.addEventListener("submit", onComposeSubmit);
        el.detail.addEventListener("click", onDetailActionClick);
        el.btnMarkRead.addEventListener("click", () => markRead(true));
        el.btnMarkUnread.addEventListener("click", () => markRead(false));
        el.btnReply.addEventListener("click", prepareReplyToSelectedMessage);
        el.btnMove.addEventListener("click", moveMessagePrompt);
        el.btnDelete.addEventListener("click", deleteSelectedMessage);
        el.btnBackToList.addEventListener("click", () => setReaderMode(false));
        el.btnSettingsClose.addEventListener("click", closeSettingsPanel);
        el.settingsForm.addEventListener("submit", onSettingsSubmit);
        el.settingsProvider.addEventListener("change", onSettingsProviderChange);
    }

    async function onLoginSubmit(event) {
        event.preventDefault();
        const username = el.loginUsername.value.trim();
        const password = el.loginPassword.value;
        if (!username || !password) {
            showToast("Email and password are required.", "error");
            return;
        }

        try {
            const resp = await requestJson("/auth/login", {
                method: "POST",
                body: JSON.stringify({username, password})
            }, false);
            saveSession(resp.accessToken, resp.refreshToken);
            showApp();
            await loadWorkspace();
            showToast("Signed in successfully.", "success");
        } catch (err) {
            showToast(err.message, "error");
        }
    }

    async function onGoogleCredentialResponse(response) {
        const credential = trimToNull(response && response.credential);
        if (!credential) {
            showToast("Google sign-in token is missing.", "error");
            return;
        }

        try {
            const resp = await requestJson("/auth/google", {
                method: "POST",
                body: JSON.stringify({idToken: credential})
            }, false);
            saveSession(resp.accessToken, resp.refreshToken);
            showApp();
            await loadWorkspace();
            showToast("Signed in with Google.", "success");
        } catch (err) {
            showToast(err.message, "error");
        }
    }

    async function setupGoogleLogin() {
        if (!el.googleSigninButton || !el.googleSigninHint) {
            return;
        }
        el.googleSigninButton.innerHTML = "";
        const clientId = await resolveGoogleClientId();
        if (!clientId) {
            el.googleSigninHint.textContent = "Google sign-in is not configured.";
            el.googleSigninHint.classList.remove("hidden");
            return;
        }

        try {
            await ensureGoogleScript();
            if (!window.google || !window.google.accounts || !window.google.accounts.id) {
                throw new Error("Google Identity Services unavailable.");
            }
            el.googleSigninHint.classList.add("hidden");
            window.google.accounts.id.initialize({
                client_id: clientId,
                callback: (googleResponse) => {
                    void onGoogleCredentialResponse(googleResponse);
                },
                ux_mode: "popup",
                auto_select: false,
                cancel_on_tap_outside: true
            });
            window.google.accounts.id.renderButton(el.googleSigninButton, {
                theme: "outline",
                size: "large",
                shape: "pill",
                text: "continue_with",
                width: 320
            });
        } catch (err) {
            el.googleSigninHint.textContent = "Could not load Google sign-in.";
            el.googleSigninHint.classList.remove("hidden");
        }
    }

    async function resolveGoogleClientId() {
        if (googleClientId) {
            return googleClientId;
        }
        if (!authConfigPromise) {
            authConfigPromise = requestJson("/auth/config", {}, false)
                .then((config) => {
                    googleClientId = trimToNull(config.googleClientId) || "";
                    return googleClientId;
                })
                .catch(() => "");
        }
        const resolved = await authConfigPromise;
        authConfigPromise = null;
        return resolved;
    }

    function ensureGoogleScript() {
        if (window.google && window.google.accounts && window.google.accounts.id) {
            return Promise.resolve();
        }
        if (googleScriptPromise) {
            return googleScriptPromise;
        }

        googleScriptPromise = new Promise((resolve, reject) => {
            const existing = document.querySelector('script[data-google-identity="true"]');
            if (existing) {
                existing.addEventListener("load", resolve, {once: true});
                existing.addEventListener("error", reject, {once: true});
                return;
            }

            const script = document.createElement("script");
            script.src = "https://accounts.google.com/gsi/client";
            script.async = true;
            script.defer = true;
            script.dataset.googleIdentity = "true";
            script.addEventListener("load", resolve, {once: true});
            script.addEventListener("error", reject, {once: true});
            document.head.appendChild(script);
        });

        return googleScriptPromise;
    }

    async function loadWorkspace() {
        await ensureMailboxSettings();
        if (state.mailSettingsConfigured) {
            await loadFolders();
            await loadMessages();
        } else {
            renderDisconnectedWorkspace();
        }
        renderSessionMeta();
    }

    async function ensureMailboxSettings() {
        const data = await requestJson("/mail/settings");
        fillSettingsForm(data);
        state.mailSettingsConfigured = Boolean(data.configured);
        state.settingsLocked = !state.mailSettingsConfigured;
        if (state.mailSettingsConfigured) {
            closeSettingsPanel(true);
        } else {
            openSettingsPanel(true);
            showToast("Configure mailbox settings first.", "error");
        }
    }

    async function onSettingsSubmit(event) {
        event.preventDefault();
        const payload = collectSettingsPayload();
        const saved = await requestJson("/mail/settings", {
            method: "PUT",
            body: JSON.stringify(payload)
        });
        fillSettingsForm(saved);
        state.mailSettingsConfigured = true;
        state.settingsLocked = false;
        closeSettingsPanel(true);
        showToast("Mailbox settings saved.", "success");
        clearWorkspaceData();
        await loadFolders();
        await loadMessages();
    }

    function collectSettingsPayload() {
        const password = trimToNull(el.settingsPassword.value);
        return {
            provider: normalizeProviderValue(el.settingsProvider.value, el.settingsUsername.value),
            imapHost: trimToNull(el.settingsImapHost.value) || "",
            imapPort: Number.parseInt(el.settingsImapPort.value, 10) || 993,
            imapSsl: el.settingsImapSsl.checked,
            smtpHost: trimToNull(el.settingsSmtpHost.value) || "",
            smtpPort: Number.parseInt(el.settingsSmtpPort.value, 10) || 465,
            smtpStarttls: el.settingsSmtpStarttls.checked,
            smtpSsl: el.settingsSmtpSsl.checked,
            username: trimToNull(el.settingsUsername.value) || "",
            password: password,
            fromEmail: trimToNull(el.settingsFromEmail.value),
            defaultFolder: trimToNull(el.settingsDefaultFolder.value) || "INBOX",
            timeoutSeconds: Number.parseInt(el.settingsTimeoutSeconds.value, 10) || 30
        };
    }

    function fillSettingsForm(data) {
        const provider = normalizeProviderValue(data.provider, data.username, data.imapHost, data.smtpHost);
        el.settingsProvider.value = provider;
        el.settingsImapHost.value = data.imapHost || "";
        el.settingsImapPort.value = String(data.imapPort || 993);
        el.settingsImapSsl.checked = data.imapSsl !== false;
        el.settingsSmtpHost.value = data.smtpHost || "";
        el.settingsSmtpPort.value = String(data.smtpPort || 465);
        el.settingsSmtpStarttls.checked = Boolean(data.smtpStarttls);
        el.settingsSmtpSsl.checked = data.smtpSsl !== false;
        el.settingsUsername.value = data.username || "";
        el.settingsFromEmail.value = data.fromEmail || "";
        el.settingsDefaultFolder.value = data.defaultFolder || "INBOX";
        el.settingsTimeoutSeconds.value = String(data.timeoutSeconds || 30);
        el.settingsPassword.value = "";
        applyProviderUi(provider);
    }

    function openSettingsPanel(forceRequired) {
        if (forceRequired) {
            state.settingsLocked = true;
        }
        el.settingsPanel.classList.remove("hidden");
        el.settingsHint.textContent = state.settingsLocked
            ? "Mailbox settings are required before loading messages."
            : "Update settings and save to reconnect.";
        applyProviderUi(normalizeProviderValue(el.settingsProvider.value, el.settingsUsername.value));
    }

    function closeSettingsPanel(force) {
        if (!force && state.settingsLocked) {
            showToast("Save valid mailbox settings first.", "error");
            return;
        }
        el.settingsPanel.classList.add("hidden");
    }

    function onSettingsProviderChange() {
        const provider = normalizeProviderValue(el.settingsProvider.value, el.settingsUsername.value);
        el.settingsProvider.value = provider;
        applyProviderUi(provider);
    }

    function applyProviderUi(provider) {
        const gmail = provider === MAIL_PROVIDERS.gmail;
        if (gmail) {
            el.settingsImapHost.value = GMAIL_PRESET.imapHost;
            el.settingsImapPort.value = String(GMAIL_PRESET.imapPort);
            el.settingsImapSsl.checked = GMAIL_PRESET.imapSsl;
            el.settingsSmtpHost.value = GMAIL_PRESET.smtpHost;
            el.settingsSmtpPort.value = String(GMAIL_PRESET.smtpPort);
            el.settingsSmtpStarttls.checked = GMAIL_PRESET.smtpStarttls;
            el.settingsSmtpSsl.checked = GMAIL_PRESET.smtpSsl;
        }

        setServerFieldsDisabled(gmail);
        el.settingsPasswordLabel.textContent = gmail ? "Google App Password" : "Mailbox Password";
        el.settingsPassword.placeholder = gmail
            ? "Enter 16-character Google App Password or leave empty to keep saved value"
            : "Leave empty to keep saved password";
        el.settingsProviderHint.textContent = gmail
            ? "Use your Gmail address and a 16-character Google App Password. Your normal Google password will not work here."
            : "Use your mailbox server credentials. For corporate mail, enter the IMAP and SMTP settings provided by your admin.";
    }

    function setServerFieldsDisabled(disabled) {
        [
            el.settingsImapHost,
            el.settingsImapPort,
            el.settingsImapSsl,
            el.settingsSmtpHost,
            el.settingsSmtpPort,
            el.settingsSmtpStarttls,
            el.settingsSmtpSsl
        ].forEach((field) => {
            field.disabled = disabled;
        });
    }

    function clearWorkspaceData() {
        window.clearTimeout(state.searchDebounceTimer);
        state.folders = [];
        state.messages = [];
        state.total = 0;
        state.offset = 0;
        state.selectedUid = null;
        state.selectedDetail = null;
        state.selectedThread = null;
        state.selectedThreadMessageKey = null;
        clearMessageCache();
        setReaderMode(false);
        renderDetail(null);
    }

    function renderDisconnectedWorkspace() {
        clearWorkspaceData();
        el.folders.innerHTML = '<div class="muted">Open "Mail Settings" to connect mailbox.</div>';
        renderMessages();
    }

    async function loadFolders() {
        if (!state.mailSettingsConfigured) {
            return;
        }
        const folders = await requestJson("/mail/folders");
        state.folders = Array.isArray(folders) ? folders : [];
        if (!state.folders.includes(state.folder)) {
            state.folder = state.folders[0] || "INBOX";
            clearMessageCache();
        }
        renderFolders();
    }

    async function loadMessages() {
        if (!state.mailSettingsConfigured) {
            return;
        }
        const reqId = ++state.messagePageReqId;
        const currentOffset = state.offset;
        const data = await getMessagePage(currentOffset);

        if (reqId !== state.messagePageReqId || currentOffset !== state.offset) {
            return;
        }

        state.messages = data.messages || [];
        state.total = data.total || 0;
        if (state.selectedUid && !state.selectedThread && !state.messages.find((item) => item.uid === state.selectedUid)) {
            state.selectedUid = null;
            state.selectedDetail = null;
            state.selectedThread = null;
            state.selectedThreadMessageKey = null;
            setReaderMode(false);
            renderDetail(null);
        }
        renderMessages();

        const nextOffset = state.offset + state.limit;
        if (nextOffset < state.total) {
            void getMessagePage(nextOffset).catch(() => {
            });
        }
    }

    async function selectMessage(uid) {
        state.selectedUid = uid;
        state.selectedThreadMessageKey = null;
        renderMessages();
        const q = new URLSearchParams({folder: state.folder});
        const thread = await requestJson(`/mail/messages/${uid}/thread?${q.toString()}`);
        state.selectedThread = normalizeThreadResponse(thread, uid, state.folder);
        state.selectedThreadMessageKey = buildThreadMessageRef(state.folder, uid);
        syncSelectedDetail();
        renderDetail(state.selectedThread);
        setReaderMode(true);
    }

    async function markRead(read) {
        const selectedMessage = state.selectedDetail;
        if (!selectedMessage) {
            return;
        }
        const q = new URLSearchParams({folder: resolveMessageFolder(selectedMessage)});
        await requestJson(`/mail/messages/${selectedMessage.uid}/read?${q.toString()}`, {
            method: "POST",
            body: JSON.stringify({read})
        });
        showToast(read ? "Message marked as read." : "Message marked as unread.", "success");
        clearMessageCache();
        await loadMessages();
        await selectMessage(state.selectedUid);
    }

    async function moveMessagePrompt() {
        const selectedMessage = state.selectedDetail;
        if (!selectedMessage) {
            return;
        }
        const target = window.prompt("Move selected message to folder:", "INBOX");
        if (!target || !target.trim()) {
            return;
        }
        const q = new URLSearchParams({folder: resolveMessageFolder(selectedMessage)});
        await requestJson(`/mail/messages/${selectedMessage.uid}/move?${q.toString()}`, {
            method: "POST",
            body: JSON.stringify({targetFolder: target.trim()})
        });
        showToast(`Moved to "${displayFolderName(target.trim())}".`, "success");
        state.selectedUid = null;
        state.selectedDetail = null;
        state.selectedThread = null;
        state.selectedThreadMessageKey = null;
        clearMessageCache();
        renderDetail(null);
        setReaderMode(false);
        await loadFolders();
        await loadMessages();
    }

    async function deleteSelectedMessage() {
        const selectedMessage = state.selectedDetail;
        if (!selectedMessage) {
            return;
        }
        const confirmed = window.confirm("Delete selected message?");
        if (!confirmed) {
            return;
        }
        const q = new URLSearchParams({folder: resolveMessageFolder(selectedMessage)});
        await requestJson(`/mail/messages/${selectedMessage.uid}?${q.toString()}`, {
            method: "DELETE"
        });
        showToast("Message deleted.", "success");
        state.selectedUid = null;
        state.selectedDetail = null;
        state.selectedThread = null;
        state.selectedThreadMessageKey = null;
        clearMessageCache();
        renderDetail(null);
        setReaderMode(false);
        await loadMessages();
    }

    async function onComposeSubmit(event) {
        event.preventDefault();
        const payload = await buildComposePayload();
        await requestJson("/mail/send", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        showToast("Message sent.", "success");
        resetComposeForm();
        closeComposePanel();
    }

    async function buildComposePayload() {
        const attachments = [];
        const files = Array.from(el.attachmentsInput.files || []);
        for (const file of files) {
            const bytes = new Uint8Array(await file.arrayBuffer());
            attachments.push({
                filename: file.name,
                contentBase64: bytesToBase64(bytes),
                contentType: file.type || "application/octet-stream"
            });
        }

        const textBody = trimToNull(el.textBodyInput.value);
        if (!textBody) {
            throw new Error("Message body is required.");
        }

        const payload = {
            to: parseEmailList(el.toInput.value),
            subject: el.subjectInput.value || "",
            bodyText: textBody,
            attachments
        };

        if (state.composeReplyContext) {
            if (state.composeReplyContext.inReplyTo) {
                payload.inReplyTo = state.composeReplyContext.inReplyTo;
            }
            if (state.composeReplyContext.references) {
                payload.references = state.composeReplyContext.references;
            }
        }
        return payload;
    }

    async function testConnection() {
        if (!state.mailSettingsConfigured) {
            openSettingsPanel(true);
            showToast("Configure mailbox settings first.", "error");
            return;
        }
        const data = await requestJson("/mail/test-connection", {method: "POST"});
        showToast(data.status === "connected" ? "Mail connection is healthy." : "Connection check completed.", "success");
    }

    async function requestJson(url, options = {}, useAuth = true) {
        const response = await apiFetch(url, options, useAuth);
        const text = await response.text();
        const data = tryParseJson(text);
        if (!response.ok) {
            const detail = data && typeof data.detail === "string" ? data.detail : `${response.status} ${response.statusText}`;
            const normalizedDetail = (detail || "").toLowerCase();
            if (response.status === 428 || normalizedDetail.includes("mailbox settings not configured")) {
                state.mailSettingsConfigured = false;
                openSettingsPanel(true);
            }
            const error = new Error(detail);
            error.status = response.status;
            throw error;
        }
        return data || {};
    }

    async function apiFetch(url, options = {}, useAuth = true, retryOn401 = true) {
        const headers = {...(options.headers || {})};
        if (!headers["Content-Type"] && options.body) {
            headers["Content-Type"] = "application/json";
        }
        if (useAuth && state.accessToken) {
            headers.Authorization = `Bearer ${state.accessToken}`;
        }
        const response = await fetchWithLoader(url, {...options, headers});
        if (response.status === 401 && useAuth && retryOn401 && state.refreshToken) {
            const refreshed = await refreshAccessToken(true).catch(() => false);
            if (refreshed) {
                headers.Authorization = `Bearer ${state.accessToken}`;
                return fetchWithLoader(url, {...options, headers});
            }
        }
        return response;
    }

    async function refreshAccessToken(silent) {
        if (!state.refreshToken) {
            if (!silent) {
                showToast("Refresh token is missing.", "error");
            }
            return false;
        }
        const response = await fetchWithLoader("/auth/refresh", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({refreshToken: state.refreshToken})
        });
        const data = tryParseJson(await response.text());
        if (!response.ok || !data || !data.accessToken) {
            if (!silent) {
                showToast((data && data.detail) || "Token refresh failed.", "error");
            }
            logout(true);
            return false;
        }
        saveSession(data.accessToken, data.refreshToken || state.refreshToken);
        return true;
    }

    function restoreSession() {
        state.accessToken = localStorage.getItem(STORAGE_KEYS.access);
        state.refreshToken = localStorage.getItem(STORAGE_KEYS.refresh);
    }

    function saveSession(accessToken, refreshToken) {
        state.accessToken = accessToken;
        state.refreshToken = refreshToken;
        localStorage.setItem(STORAGE_KEYS.access, accessToken);
        localStorage.setItem(STORAGE_KEYS.refresh, refreshToken);
        renderSessionMeta();
    }

    function logout(silent) {
        window.clearTimeout(state.searchDebounceTimer);
        state.accessToken = null;
        state.refreshToken = null;
        state.selectedUid = null;
        state.selectedDetail = null;
        state.selectedThread = null;
        state.selectedThreadMessageKey = null;
        state.composeReplyContext = null;
        state.mailSettingsConfigured = false;
        state.settingsLocked = false;
        setReaderMode(false);
        resetComposeForm();
        closeComposePanel();
        closeSettingsPanel(true);
        localStorage.removeItem(STORAGE_KEYS.access);
        localStorage.removeItem(STORAGE_KEYS.refresh);
        showLogin();
        if (!silent) {
            showToast("Logged out.", "success");
        }
    }

    function showLogin() {
        el.loginView.classList.remove("hidden");
        el.appView.classList.add("hidden");
        void setupGoogleLogin();
    }

    function showApp() {
        el.loginView.classList.add("hidden");
        el.appView.classList.remove("hidden");
    }

    function renderSessionMeta() {
        const payload = decodeJwtPayload(state.accessToken);
        if (!payload) {
            el.sessionMeta.textContent = "Session: not loaded";
            return;
        }
        const username = payload.sub || "unknown";
        const roles = Array.isArray(payload.roles)
            ? payload.roles.join(",")
            : (typeof payload.roles === "string" ? payload.roles : "");
        const expiresAt = payload.exp ? new Date(payload.exp * 1000).toLocaleString() : "n/a";
        el.sessionMeta.innerHTML = `
            <span class="session-label">Signed In As</span>
            <strong class="session-user">${escapeHtml(username)}</strong>
            <span class="session-line">Roles: ${escapeHtml(roles || "none")}</span>
            <span class="session-line">Expires: ${escapeHtml(expiresAt)}</span>
        `;
    }

    function renderFolders() {
        el.folders.innerHTML = "";
        if (!state.folders.length) {
            el.folders.innerHTML = '<div class="muted">No folders found.</div>';
            return;
        }
        sortFolders(state.folders).forEach((folderName) => {
            const item = document.createElement("button");
            item.type = "button";
            item.className = `folder-item ${state.folder === folderName ? "active" : ""}`;
            const label = displayFolderName(folderName);
            item.innerHTML = `
                <span class="folder-icon">${folderIcon(folderName)}</span>
                <span class="folder-copy">
                    <span class="folder-name">${escapeHtml(label)}</span>
                    <span class="folder-path">${escapeHtml(folderName)}</span>
                </span>
            `;
            item.addEventListener("click", async () => {
                state.folder = folderName;
                state.offset = 0;
                state.selectedUid = null;
                state.selectedDetail = null;
                clearMessageCache();
                renderDetail(null);
                setReaderMode(false);
                renderFolders();
                await loadMessages();
            });
            el.folders.appendChild(item);
        });
    }

    function renderMessages() {
        el.messages.innerHTML = "";
        const visibleMessages = getVisibleMessages();
        const start = state.total === 0 ? 0 : state.offset + 1;
        const end = Math.min(state.offset + state.limit, state.total);
        const filterSuffix = state.searchQuery ? ` | search: ${state.searchQuery}` : "";
        el.messageMeta.textContent = `${start}-${end} of ${state.total} in ${displayFolderName(state.folder)}${filterSuffix}`;

        if (!visibleMessages.length) {
            el.messages.innerHTML = '<div class="muted">No messages found.</div>';
        } else {
            visibleMessages.forEach((message) => {
                const item = document.createElement("article");
                item.className = `message-item ${state.selectedUid === message.uid ? "active" : ""} ${message.seen ? "" : "unread"}`.trim();
                const snippet = trimToNull(message.snippet) || "No preview available";
                const subject = trimToNull(message.subject) || "(no subject)";
                item.innerHTML = `
                    <span class="row-badge" aria-hidden="true"></span>
                    <div class="row-main">
                        <div class="row-top">
                            <div class="row-from">${escapeHtml(message.fromEmail || "unknown")}</div>
                            <div class="row-date">${escapeHtml(formatListDate(message.date))}</div>
                        </div>
                        <div class="row-content">
                            <span class="subject">${escapeHtml(subject)}</span>
                            <span class="snippet">${escapeHtml(snippet)}</span>
                        </div>
                    </div>
                `;
                item.addEventListener("click", () => {
                    selectMessage(message.uid).catch((err) => showToast(err.message, "error"));
                });
                el.messages.appendChild(item);
            });
        }
        el.btnPrevPage.disabled = state.offset <= 0;
        el.btnNextPage.disabled = state.offset + state.limit >= state.total;
    }

    function renderDetail(thread) {
        const selected = getSelectedThreadMessage(thread);
        const hasSelected = !!selected;
        el.btnMarkRead.disabled = !hasSelected;
        el.btnMarkUnread.disabled = !hasSelected;
        el.btnReply.disabled = !hasSelected || !trimToNull(selected.fromEmail);
        el.btnMove.disabled = !hasSelected;
        el.btnDelete.disabled = !hasSelected;

        if (!thread || !Array.isArray(thread.messages) || !thread.messages.length || !selected) {
            el.detail.className = "detail-empty";
            el.detail.textContent = "Select a message to read.";
            el.btnReply.disabled = true;
            return;
        }

        const messages = thread.messages;
        const threadItems = messages.map((message) => {
            const active = threadMessageKey(message) === threadMessageKey(selected);
            const preview = escapeHtml(buildThreadPreview(message));
            const toLineRaw = Array.isArray(message.to) ? message.to.join(", ") : "";
            const toLine = escapeHtml(toLineRaw || "-");
            const attachmentItems = normalizeAttachments(message.attachments);
            const attachmentCount = attachmentItems.length;
            const bodyPayload = active ? getReadableBodyPayload(message) : null;
            const attachmentList = attachmentItems.length
                ? `
                    <div class="thread-attachments">
                        <div class="thread-attachments-head">
                            <strong>Attachments</strong>
                            <span class="detail-block-note">${attachmentItems.length} file${attachmentItems.length === 1 ? "" : "s"}</span>
                        </div>
                        <ul class="attachment-list">${attachmentItems.map((item) => {
                            const sizeText = item.size == null ? "" : ` <span class="mono muted">(${escapeHtml(formatBytes(item.size))})</span>`;
                            return `
                                <li class="attachment-item">
                                    <span class="attachment-name">${escapeHtml(item.filename)}${sizeText}</span>
                                    <span class="attachment-actions">
                                        <button type="button" class="btn tiny ghost" data-attachment-open="${buildAttachmentRef(message, item.index)}">Open</button>
                                        <button type="button" class="btn tiny ghost" data-attachment-download="${buildAttachmentRef(message, item.index)}">Download</button>
                                    </span>
                                </li>
                            `;
                        }).join("")}</ul>
                    </div>
                `
                : "";
            return `
                <article class="thread-message ${active ? "active" : ""}" data-thread-select="${buildThreadMessageRef(resolveMessageFolder(message), message.uid)}">
                    <div class="thread-message-shell">
                        <div class="thread-avatar" aria-hidden="true">${escapeHtml(buildAvatarLabel(message.fromEmail || "unknown"))}</div>
                        <div class="thread-message-content">
                            <div class="thread-message-head">
                                <div class="thread-head-main">
                                    <div class="thread-author-row">
                                        <div class="thread-author">${escapeHtml(message.fromEmail || "unknown")}</div>
                                        ${!message.seen ? '<span class="thread-pill unread">Unread</span>' : ""}
                                    </div>
                                    <div class="thread-recipient">to ${toLine}</div>
                                </div>
                                <div class="thread-head-side">
                                    ${attachmentCount ? `<span class="thread-pill">${attachmentCount} file${attachmentCount === 1 ? "" : "s"}</span>` : ""}
                                    <span class="thread-date">${escapeHtml(formatDate(message.date))}</span>
                                </div>
                            </div>
                            ${active ? `
                                <div class="thread-message-expanded">
                                    <div class="thread-meta-inline">
                                        <span><strong>From:</strong> ${escapeHtml(message.fromEmail || "-")}</span>
                                        <span><strong>To:</strong> ${toLine}</span>
                                        <span><strong>Date:</strong> ${escapeHtml(formatDate(message.date))}</span>
                                    </div>
                                    <div class="thread-message-body">
                                        <div class="thread-body-head">
                                            <strong>Message</strong>
                                            <span class="detail-block-note">${bodyPayload.type === "html" ? "Rendered HTML" : "Plain text"}</span>
                                        </div>
                                        <div class="mail-body-host" data-message-body-host="${buildThreadMessageRef(resolveMessageFolder(message), message.uid)}"></div>
                                    </div>
                                    ${attachmentList}
                                </div>
                            ` : `
                                <div class="thread-preview">${preview}</div>
                            `}
                        </div>
                    </div>
                </article>
            `;
        }).join("");

        el.detail.className = "detail";
        el.detail.innerHTML = `
            <div class="detail-hero">
                <div class="detail-status mono">${selected.seen ? "Read" : "Unread"} · UID ${selected.uid}</div>
                <h4>${escapeHtml(selected.subject || "(no subject)")}</h4>
                <div class="thread-summary">
                    <span class="thread-summary-badge">Conversation</span>
                    <span class="thread-summary-copy">${messages.length} message${messages.length === 1 ? "" : "s"} in this thread</span>
                </div>
            </div>
            <div class="thread-stack">${threadItems}</div>
        `;
        renderMessageBody(getReadableBodyPayload(selected), threadMessageKey(selected));
    }

    function normalizeThreadResponse(thread, anchorUid, folder) {
        const resolvedFolder = trimToNull(thread && thread.folder) || folder;
        const messages = Array.isArray(thread && thread.messages)
            ? thread.messages.map((message) => ({
                ...message,
                folder: trimToNull(message && message.folder) || resolvedFolder
            }))
            : [];
        return {
            anchorUid,
            folder: resolvedFolder,
            total: typeof thread?.total === "number" ? thread.total : messages.length,
            messages
        };
    }

    function syncSelectedDetail() {
        const selected = getSelectedThreadMessage(state.selectedThread);
        if (selected) {
            state.selectedDetail = selected;
            state.selectedThreadMessageKey = threadMessageKey(selected);
            return;
        }
        const fallback = state.selectedThread && Array.isArray(state.selectedThread.messages)
            ? state.selectedThread.messages[state.selectedThread.messages.length - 1] || null
            : null;
        state.selectedDetail = fallback;
        state.selectedThreadMessageKey = fallback ? threadMessageKey(fallback) : null;
    }

    function getSelectedThreadMessage(thread) {
        if (!thread || !Array.isArray(thread.messages) || !thread.messages.length) {
            return null;
        }
        return getThreadMessageByKey(thread, state.selectedThreadMessageKey)
            || getThreadMessageByFolderAndUid(thread, state.folder, state.selectedUid)
            || thread.messages[thread.messages.length - 1]
            || null;
    }

    function getThreadMessageByUid(thread, uid) {
        if (!thread || !Array.isArray(thread.messages) || !uid) {
            return null;
        }
        return thread.messages.find((message) => message.uid === uid) || null;
    }

    function getThreadMessageByFolderAndUid(thread, folder, uid) {
        if (!thread || !Array.isArray(thread.messages) || !uid) {
            return null;
        }
        const expectedFolder = trimToNull(folder);
        return thread.messages.find((message) => {
            if (message.uid !== uid) {
                return false;
            }
            return !expectedFolder || resolveMessageFolder(message) === expectedFolder;
        }) || null;
    }

    function getThreadMessageByKey(thread, key) {
        const ref = parseThreadMessageRef(key);
        if (!ref) {
            return null;
        }
        return getThreadMessageByFolderAndUid(thread, ref.folder, ref.uid);
    }

    function buildThreadPreview(detail) {
        const textBody = trimToNull(detail && detail.textBody);
        if (textBody) {
            return textBody.replace(/\s+/g, " ").trim();
        }
        const htmlBody = trimToNull(detail && detail.htmlBody);
        if (htmlBody) {
            return stripHtml(htmlBody).replace(/\s+/g, " ").trim();
        }
        return "Open this message to read the full content.";
    }

    function buildAvatarLabel(value) {
        const source = trimToNull(value) || "?";
        const mailbox = source.split("@")[0] || source;
        const compact = mailbox.replace(/[^a-zA-Z0-9]+/g, " ").trim();
        const tokens = compact.split(/\s+/).filter(Boolean);
        if (tokens.length >= 2) {
            return `${tokens[0][0]}${tokens[1][0]}`.toUpperCase();
        }
        return mailbox.slice(0, 2).toUpperCase();
    }

    function resolveMessageFolder(message) {
        return trimToNull(message && message.folder)
            || trimToNull(state.selectedThread && state.selectedThread.folder)
            || state.folder;
    }

    function buildThreadMessageRef(folder, uid) {
        return `${encodeURIComponent(folder || state.folder)}::${uid}`;
    }

    function threadMessageKey(message) {
        if (!message) {
            return null;
        }
        return buildThreadMessageRef(resolveMessageFolder(message), message.uid);
    }

    function parseThreadMessageRef(value) {
        const text = trimToNull(value);
        if (!text || !text.includes("::")) {
            return null;
        }
        const [folderText, uidText] = text.split("::", 2);
        const folder = decodeURIComponent(folderText);
        const uid = Number.parseInt(uidText, 10);
        if (!folder || !Number.isInteger(uid)) {
            return null;
        }
        return {folder, uid};
    }

    function buildAttachmentRef(message, index) {
        return `${encodeURIComponent(resolveMessageFolder(message))}::${message.uid}::${index}`;
    }

    function setReaderMode(enabled) {
        state.readerMode = Boolean(enabled);
        el.mailListCard.classList.toggle("hidden", state.readerMode);
        el.detailCard.classList.toggle("hidden", !state.readerMode);
    }

    function renderMessageBody(bodyPayload, messageRef) {
        const host = messageRef
            ? document.querySelector(`[data-message-body-host="${CSS.escape(String(messageRef))}"]`)
            : document.querySelector("[data-message-body-host]");
        if (!host) {
            return;
        }

        if (bodyPayload.type === "html") {
            const frame = document.createElement("iframe");
            frame.className = "mail-body-frame";
            frame.setAttribute("sandbox", "allow-popups allow-popups-to-escape-sandbox");
            frame.setAttribute("referrerpolicy", "no-referrer");
            frame.srcdoc = wrapBodyHtmlDocument(bodyPayload.value);
            host.appendChild(frame);
            return;
        }

        const pre = document.createElement("pre");
        pre.className = "mail-body-pre";
        pre.textContent = bodyPayload.type === "text" ? bodyPayload.value : "(empty)";
        host.appendChild(pre);
    }

    function getReadableBodyPayload(detail) {
        const textBody = trimToNull(detail.textBody);
        const htmlBody = trimToNull(detail.htmlBody);

        if (htmlBody && looksLikeHtml(htmlBody)) {
            return {type: "html", value: sanitizeEmailHtml(htmlBody)};
        }
        if (textBody && looksLikeHtml(textBody)) {
            return {type: "html", value: sanitizeEmailHtml(textBody)};
        }
        if (textBody) {
            return {type: "text", value: textBody};
        }
        if (htmlBody) {
            return {type: "text", value: stripHtml(htmlBody)};
        }
        return {type: "empty", value: ""};
    }

    function wrapBodyHtmlDocument(content) {
        return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <base target="_blank">
  <style>
    :root { color-scheme: light; }
    body {
      margin: 0;
      padding: 14px;
      font: 14px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
      color: #202124;
      background: #fff;
      word-wrap: break-word;
      overflow-wrap: anywhere;
    }
    img, table, iframe, video { max-width: 100%; height: auto; }
    a { color: #1a73e8; }
    pre { white-space: pre-wrap; }
  </style>
</head>
<body>${content}</body>
</html>`;
    }

    function sanitizeEmailHtml(html) {
        return html
            .replace(/<script[\s\S]*?<\/script>/gi, "")
            .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
            .replace(/<object[\s\S]*?<\/object>/gi, "")
            .replace(/<embed[\s\S]*?>/gi, "")
            .replace(/\s+on\w+\s*=\s*(['"]).*?\1/gi, "")
            .replace(/\s+on\w+\s*=\s*[^\s>]+/gi, "")
            .replace(/(href|src)\s*=\s*(['"])\s*javascript:[^'"]*\2/gi, '$1="#"');
    }

    function showToast(message, kind) {
        el.toast.textContent = message;
        el.toast.className = `toast ${kind || ""}`;
        setTimeout(() => {
            el.toast.classList.add("hidden");
        }, 3000);
    }

    function openComposeForNewMessage() {
        state.composeReplyContext = null;
        resetComposeForm();
        renderComposeMode();
        el.composePanel.classList.remove("hidden");
    }

    function closeComposePanel() {
        el.composePanel.classList.add("hidden");
    }

    function prepareReplyToSelectedMessage() {
        const detail = state.selectedDetail;
        if (!detail) {
            return;
        }

        const fromEmail = trimToNull(detail.fromEmail);
        if (!fromEmail) {
            showToast("Reply address not found in selected message.", "error");
            return;
        }

        state.composeReplyContext = buildReplyContext(detail);
        resetComposeForm();
        el.toInput.value = fromEmail;
        el.subjectInput.value = buildReplySubject(detail.subject);
        el.textBodyInput.value = buildReplyQuote(detail);
        renderComposeMode();
        el.composePanel.classList.remove("hidden");
        el.textBodyInput.focus();
        el.textBodyInput.setSelectionRange(0, 0);
    }

    function buildReplyContext(detail) {
        const messageId = trimToNull(detail.messageId);
        const inReplyTo = messageId || trimToNull(detail.inReplyTo);
        const existingReferences = trimToNull(detail.references);
        let references = existingReferences;
        if (messageId) {
            references = references ? `${references} ${messageId}` : messageId;
        } else if (inReplyTo && !references) {
            references = inReplyTo;
        }
        return {
            inReplyTo,
            references
        };
    }

    function buildReplySubject(subject) {
        const normalized = (subject || "").trim();
        if (!normalized) {
            return "Re:";
        }
        if (/^re\s*:/i.test(normalized)) {
            return normalized;
        }
        return `Re: ${normalized}`;
    }

    function buildReplyQuote(detail) {
        const dateText = formatDate(detail.date);
        const sender = trimToNull(detail.fromEmail) || "unknown";
        const body = trimToNull(detail.textBody) || trimToNull(stripHtml(detail.htmlBody)) || "";
        const quoted = body
            .split(/\r?\n/)
            .map((line) => `> ${line}`)
            .join("\n");
        return `\n\nOn ${dateText}, ${sender} wrote:\n${quoted || ">"}`;
    }

    function resetComposeForm() {
        el.composeForm.reset();
        renderComposeMode();
    }

    function renderComposeMode() {
        if (!state.composeReplyContext) {
            el.composeModeMeta.textContent = "";
            el.composeModeMeta.classList.add("hidden");
            return;
        }
        el.composeModeMeta.textContent = "Reply mode enabled (thread headers will be sent).";
        el.composeModeMeta.classList.remove("hidden");
    }

    async function onDetailActionClick(event) {
        const openButton = event.target.closest("[data-attachment-open]");
        if (openButton) {
            const attachmentRef = parseAttachmentReference(openButton.getAttribute("data-attachment-open"));
            if (attachmentRef) {
                await openAttachment(attachmentRef.uid, attachmentRef.index, attachmentRef.folder);
            }
            return;
        }

        const downloadButton = event.target.closest("[data-attachment-download]");
        if (downloadButton) {
            const attachmentRef = parseAttachmentReference(downloadButton.getAttribute("data-attachment-download"));
            if (attachmentRef) {
                await downloadAttachment(attachmentRef.uid, attachmentRef.index, attachmentRef.folder);
            }
            return;
        }

        const threadCard = event.target.closest("[data-thread-select]");
        if (threadCard) {
            const ref = parseThreadMessageRef(threadCard.getAttribute("data-thread-select"));
            if (ref) {
                const nextKey = buildThreadMessageRef(ref.folder, ref.uid);
                if (nextKey === state.selectedThreadMessageKey) {
                    return;
                }
                state.selectedThreadMessageKey = nextKey;
                syncSelectedDetail();
                renderMessages();
                renderDetail(state.selectedThread);
            }
        }
    }

    async function openAttachment(messageUid, index, folderName) {
        try {
            const {blob, filename} = await fetchAttachment(messageUid, index, folderName);
            const objectUrl = URL.createObjectURL(blob);
            const popup = window.open(objectUrl, "_blank", "noopener,noreferrer");
            if (!popup) {
                triggerDownload(objectUrl, filename);
            }
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        } catch (err) {
            showToast(err.message, "error");
        }
    }

    async function downloadAttachment(messageUid, index, folderName) {
        try {
            const {blob, filename} = await fetchAttachment(messageUid, index, folderName);
            const objectUrl = URL.createObjectURL(blob);
            triggerDownload(objectUrl, filename);
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        } catch (err) {
            showToast(err.message, "error");
        }
    }

    async function fetchAttachment(messageUid, index, folderName) {
        if (!messageUid) {
            throw new Error("Select a message first.");
        }
        const q = new URLSearchParams({folder: folderName || state.folder});
        const response = await apiFetch(`/mail/messages/${messageUid}/attachments/${index}?${q.toString()}`, {
            method: "GET"
        });
        if (!response.ok) {
            const body = tryParseJson(await response.text());
            const detail = body && typeof body.detail === "string"
                ? body.detail
                : `${response.status} ${response.statusText}`;
            throw new Error(detail);
        }
        const blob = await response.blob();
        const filename = extractFilenameFromDisposition(response.headers.get("content-disposition"))
            || findAttachmentFilename(messageUid, index, folderName)
            || `attachment-${index + 1}`;
        return {blob, filename};
    }

    function findAttachmentFilename(messageUid, index, folderName) {
        const message = getThreadMessageByFolderAndUid(state.selectedThread, folderName, messageUid)
            || getThreadMessageByUid(state.selectedThread, messageUid);
        const attachments = normalizeAttachments(message ? message.attachments : []);
        const matched = attachments.find((item) => item.index === index);
        return matched ? matched.filename : null;
    }

    function parseAttachmentReference(value) {
        const text = trimToNull(value);
        if (!text) {
            return null;
        }
        const [folderText, uidText, indexText] = text.split("::");
        const folder = decodeURIComponent(folderText || "");
        const uid = Number.parseInt(uidText, 10);
        const index = Number.parseInt(indexText, 10);
        if (!folder || !Number.isInteger(uid) || !Number.isInteger(index) || index < 0) {
            return null;
        }
        return {folder, uid, index};
    }

    function triggerDownload(objectUrl, filename) {
        const anchor = document.createElement("a");
        anchor.href = objectUrl;
        anchor.download = filename || "attachment";
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
    }

    async function fetchWithLoader(url, options) {
        showLoader();
        try {
            return await fetch(toApiUrl(url), options);
        } finally {
            hideLoader();
        }
    }

    function showLoader() {
        state.pendingRequests += 1;
        el.globalLoader.classList.remove("hidden");
    }

    function hideLoader() {
        state.pendingRequests = Math.max(0, state.pendingRequests - 1);
        if (state.pendingRequests === 0) {
            el.globalLoader.classList.add("hidden");
        }
    }

    function parseEmailList(value) {
        return value
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);
    }

    function normalizeAttachments(list) {
        if (!Array.isArray(list)) {
            return [];
        }
        return list.map((item, idx) => {
            if (typeof item === "string") {
                return {
                    index: idx,
                    filename: item,
                    size: null
                };
            }
            const rawIndex = Number.parseInt(item && item.index, 10);
            const index = Number.isInteger(rawIndex) && rawIndex >= 0 ? rawIndex : idx;
            const filename = trimToNull(item && item.filename) || `attachment-${index + 1}`;
            const rawSize = item && typeof item.size === "number" ? item.size : null;
            const size = Number.isFinite(rawSize) && rawSize >= 0 ? rawSize : null;
            return {
                index,
                filename,
                size
            };
        });
    }

    function getVisibleMessages() {
        return state.messages;
    }

    async function getMessagePage(offset) {
        const chunkOffset = Math.floor(offset / state.backendChunkSize) * state.backendChunkSize;
        const chunk = await fetchMessageChunk(chunkOffset);
        const start = offset - chunkOffset;
        const end = start + state.limit;
        return {
            total: chunk.total,
            messages: (chunk.messages || []).slice(start, end)
        };
    }

    function clearMessageCache() {
        state.messagePageCache.clear();
    }

    async function fetchMessageChunk(chunkOffset) {
        const key = messageChunkKey(chunkOffset);
        if (state.messagePageCache.has(key)) {
            return state.messagePageCache.get(key);
        }

        const q = new URLSearchParams({
            folder: state.folder,
            limit: String(state.backendChunkSize),
            offset: String(chunkOffset),
            unseenOnly: String(state.unseenOnly)
        });
        if (state.searchQuery) {
            q.set("query", state.searchQuery);
        }
        const data = await requestJson(`/mail/messages?${q.toString()}`);
        const normalized = {
            total: data.total || 0,
            messages: data.messages || []
        };
        state.messagePageCache.set(key, normalized);
        return normalized;
    }

    function messageChunkKey(chunkOffset) {
        return `${state.folder}|${state.unseenOnly}|${state.backendChunkSize}|${chunkOffset}|${state.searchQuery}`;
    }

    function toApiUrl(url) {
        if (!API_BASE_URL || /^https?:\/\//i.test(url)) {
            return url;
        }
        if (url.startsWith("/")) {
            return `${API_BASE_URL}${url}`;
        }
        return `${API_BASE_URL}/${url}`;
    }

    function bytesToBase64(bytes) {
        let binary = "";
        const chunk = 0x8000;
        for (let i = 0; i < bytes.length; i += chunk) {
            const sub = bytes.subarray(i, i + chunk);
            binary += String.fromCharCode.apply(null, sub);
        }
        return btoa(binary);
    }

    function tryParseJson(value) {
        if (!value) {
            return null;
        }
        try {
            return JSON.parse(value);
        } catch (err) {
            return null;
        }
    }

    function decodeJwtPayload(token) {
        if (!token || token.split(".").length < 2) {
            return null;
        }
        try {
            const base64Url = token.split(".")[1];
            const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
            const json = decodeURIComponent(atob(base64).split("").map((c) => {
                return `%${(`00${c.charCodeAt(0).toString(16)}`).slice(-2)}`;
            }).join(""));
            return JSON.parse(json);
        } catch (err) {
            return null;
        }
    }

    function trimToNull(value) {
        const text = (value || "").trim();
        return text ? text : null;
    }

    function normalizeProviderValue(provider, username, imapHost, smtpHost) {
        const normalizedProvider = trimToNull(provider);
        if (normalizedProvider && normalizedProvider.toUpperCase() === MAIL_PROVIDERS.gmail) {
            return MAIL_PROVIDERS.gmail;
        }
        const email = (trimToNull(username) || "").toLowerCase();
        const imap = (trimToNull(imapHost) || "").toLowerCase();
        const smtp = (trimToNull(smtpHost) || "").toLowerCase();
        if ((email.endsWith("@gmail.com") || email.endsWith("@googlemail.com"))
            && imap === GMAIL_PRESET.imapHost
            && smtp === GMAIL_PRESET.smtpHost) {
            return MAIL_PROVIDERS.gmail;
        }
        return MAIL_PROVIDERS.custom;
    }

    function formatBytes(value) {
        if (value == null || value < 0) {
            return "";
        }
        const units = ["B", "KB", "MB", "GB", "TB"];
        let size = value;
        let unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex += 1;
        }
        const rounded = unitIndex === 0 ? String(size) : size.toFixed(1);
        return `${rounded.replace(/\.0$/, "")} ${units[unitIndex]}`;
    }

    function extractFilenameFromDisposition(value) {
        if (!value) {
            return null;
        }
        const utfMatch = value.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
        if (utfMatch && utfMatch[1]) {
            try {
                return decodeURIComponent(utfMatch[1].trim().replace(/^"(.*)"$/, "$1"));
            } catch (err) {
                return utfMatch[1].trim().replace(/^"(.*)"$/, "$1");
            }
        }
        const plainMatch = value.match(/filename\s*=\s*([^;]+)/i);
        if (!plainMatch || !plainMatch[1]) {
            return null;
        }
        return plainMatch[1].trim().replace(/^"(.*)"$/, "$1");
    }

    function stripHtml(value) {
        if (!value) {
            return "";
        }
        const temp = document.createElement("div");
        temp.innerHTML = value;
        return (temp.textContent || temp.innerText || "").trim();
    }

    function looksLikeHtml(value) {
        return /<\/?[a-z][\s\S]*>/i.test(value || "");
    }

    function formatDate(value) {
        if (!value) {
            return "n/a";
        }
        const d = new Date(value);
        return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
    }

    function formatListDate(value) {
        if (!value) {
            return "";
        }
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) {
            return String(value);
        }
        const now = new Date();
        const sameDay = d.toDateString() === now.toDateString();
        if (sameDay) {
            return d.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit"});
        }
        return d.toLocaleDateString();
    }

    function canonicalFolderName(folderName) {
        const raw = String(folderName || "").trim();
        const withoutProvider = raw.replace(/^\[gmail\]\//i, "").trim();
        const normalized = withoutProvider.toLowerCase();

        if (normalized === "inbox") {
            return "inbox";
        }
        if (normalized === "sent" || normalized === "sent mail") {
            return "sent";
        }
        if (normalized === "drafts") {
            return "drafts";
        }
        if (normalized === "spam") {
            return "spam";
        }
        if (normalized === "trash") {
            return "trash";
        }
        if (normalized === "starred") {
            return "starred";
        }
        if (normalized === "all mail") {
            return "all_mail";
        }
        if (normalized === "important") {
            return "important";
        }
        return normalized;
    }

    function displayFolderName(folderName) {
        const canonical = canonicalFolderName(folderName);
        const labelMap = {
            inbox: "Inbox",
            sent: "Sent",
            drafts: "Drafts",
            spam: "Spam",
            trash: "Trash",
            starred: "Starred",
            all_mail: "All Mail",
            important: "Important"
        };
        if (Object.prototype.hasOwnProperty.call(labelMap, canonical)) {
            return labelMap[canonical];
        }

        const raw = String(folderName || "").trim();
        const withoutProvider = raw.replace(/^\[gmail\]\//i, "").trim();
        const unwrapped = withoutProvider.replace(/^\[(.+)]$/, "$1").trim();
        return unwrapped || raw || "Folder";
    }

    function sortFolders(folders) {
        const priority = {
            inbox: 0,
            sent: 1,
            drafts: 2,
            important: 3,
            starred: 4,
            all_mail: 5,
            spam: 6,
            trash: 7
        };
        return [...folders].sort((a, b) => {
            const ca = canonicalFolderName(a);
            const cb = canonicalFolderName(b);
            const pa = Object.prototype.hasOwnProperty.call(priority, ca) ? priority[ca] : 99;
            const pb = Object.prototype.hasOwnProperty.call(priority, cb) ? priority[cb] : 99;
            if (pa !== pb) {
                return pa - pb;
            }
            return displayFolderName(a).localeCompare(displayFolderName(b));
        });
    }

    function folderIcon(folderName) {
        const canonical = canonicalFolderName(folderName);
        const iconMap = {
            inbox: "I",
            sent: "S",
            drafts: "D",
            spam: "S",
            trash: "T",
            starred: "*",
            all_mail: "A",
            important: "!"
        };
        return iconMap[canonical] || "F";
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
})();
