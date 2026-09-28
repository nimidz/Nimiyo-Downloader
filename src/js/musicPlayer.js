/**
 * NIMIYO Local Music Player Engine
 * High-performance, offline audio playback with Soft UI + Neo-Brutalism aesthetics.
 * Full feature parity with Flow music app adapted to NIMIYO design language.
 */

class NimiyoReverbFX {
  constructor(audioElement) {
    this.audioElement = audioElement;
    this.audioCtx = null;
    this.source = null;
    this.dryGain = null;
    this.wetGain = null;
    this.fxSendGain = null;

    // Filters: DJ Low-Cut (keeps bass clean) & Dynamic Tone/Air Lowpass
    this.lowCutFilter = null;
    this.toneFilter = null;
    this.convolver = null;

    this.isEnabled = false;
    this.dryWet = 0.5;
    this.depth = 0.5;
    this.isInitialized = false;
  }

  generateDattorroImpulse(durationSec = 1.4, damping = 0.45) {
    if (!this.audioCtx) return null;
    const rate = this.audioCtx.sampleRate;
    const len = Math.floor(rate * durationSec);
    const buf = this.audioCtx.createBuffer(2, len, rate);
    const l = buf.getChannelData(0);
    const r = buf.getChannelData(1);

    // Warm exponential decay with 1-pole high-frequency air absorption
    let lpL = 0;
    let lpR = 0;
    const decayRate = 3.6; // Smooth 1.4s natural tail

    for (let i = 0; i < len; i++) {
      const t = i / rate;
      const env = Math.exp(-t * decayRate);
      const whiteL = (Math.random() * 2 - 1) * env;
      const whiteR = (Math.random() * 2 - 1) * env;

      lpL = lpL * damping + whiteL * (1 - damping);
      lpR = lpR * damping + whiteR * (1 - damping);

      l[i] = lpL * 0.85;
      r[i] = lpR * 0.85;
    }

    // Dattorro early reflections / stereo spatial diffusers
    const earlyTaps = [
      { t: 0.0047, g: 0.65, p: -0.6 },
      { t: 0.0083, g: 0.55, p:  0.6 },
      { t: 0.0127, g: 0.45, p: -0.4 },
      { t: 0.0225, g: 0.38, p:  0.4 },
      { t: 0.0359, g: 0.32, p: -0.3 },
      { t: 0.0604, g: 0.25, p:  0.3 },
    ];

    earlyTaps.forEach(tap => {
      const idx = Math.floor(tap.t * rate);
      if (idx < len) {
        l[idx] += tap.g * (1 - tap.p) * 0.5;
        r[idx] += tap.g * (1 + tap.p) * 0.5;
      }
    });

    return buf;
  }

  ensureInitialized() {
    if (this.isInitialized) {
      this.resumeIfNeeded();
      return true;
    }
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return false;
      this.audioCtx = new AudioCtx();

      this.source = this.audioCtx.createMediaElementSource(this.audioElement);
      this.dryGain = this.audioCtx.createGain();
      this.wetGain = this.audioCtx.createGain();
      this.fxSendGain = this.audioCtx.createGain();

      // 1. DJ Low-Cut Highpass (250Hz): Guarantees kick drum & bass 100% punchy without mud
      this.lowCutFilter = this.audioCtx.createBiquadFilter();
      this.lowCutFilter.type = "highpass";
      this.lowCutFilter.frequency.value = 250;
      this.lowCutFilter.Q.value = 0.707;

      // 2. Dynamic Tone / Air Shimmer Filter (Lowpass 2800Hz - 9500Hz controlled by Y-axis)
      this.toneFilter = this.audioCtx.createBiquadFilter();
      this.toneFilter.type = "lowpass";
      this.toneFilter.frequency.value = 5500;
      this.toneFilter.Q.value = 0.707;

      // 3. Stable 1.4s Dattorro Plate Convolver (Zero feedback, zero howling, 100% warm & silky)
      this.convolver = this.audioCtx.createConvolver();
      this.convolver.buffer = this.generateDattorroImpulse(1.4, 0.42);

      // Clean dry path: source -> dryGain -> destination
      this.source.connect(this.dryGain);
      this.dryGain.connect(this.audioCtx.destination);

      // Wet FX path: source -> fxSendGain -> lowCutFilter -> convolver -> toneFilter -> wetGain -> destination
      this.source.connect(this.fxSendGain);
      this.fxSendGain.connect(this.lowCutFilter);
      this.lowCutFilter.connect(this.convolver);
      this.convolver.connect(this.toneFilter);
      this.toneFilter.connect(this.wetGain);
      this.wetGain.connect(this.audioCtx.destination);

      this.applyParameters();
      this.isInitialized = true;
      this.resumeIfNeeded();
      return true;
    } catch (e) {
      console.warn("[REVERB] Web Audio initialization notice:", e);
      return false;
    }
  }

  applyParameters() {
    if (!this.audioCtx || !this.dryGain || !this.wetGain || !this.fxSendGain) return;
    const now = this.audioCtx.currentTime;

    if (!this.isEnabled) {
      // 100% HARD BYPASS: Mutes FX input completely, 0% CPU, pure dry audio
      this.dryGain.gain.setValueAtTime(1.0, now);
      this.wetGain.gain.setValueAtTime(0.0, now);
      this.fxSendGain.gain.setValueAtTime(0.0, now);
      return;
    }

    // Active FX send
    this.fxSendGain.gain.setTargetAtTime(1.0, now, 0.015);

    // Spatial Reverb X-axis (Dry / Wet):
    // 0 = 100% dry, 0% wet
    // 0.5 = 85% dry, 80% wet (sweet spot: punchy vocal with lush airy space)
    // 1.0 = 45% dry, 130% wet (DJ drop wash-out)
    const effectiveWet = Math.pow(this.dryWet, 0.85) * 1.3;
    const effectiveDry = 1.0 - (this.dryWet * 0.55);

    // Spatial Reverb Y-axis (Tone / Acoustic Space):
    // Bottom (Y=0): Warm, intimate, mellow club space (Cutoff ~2800 Hz)
    // Top (Y=1): Open, airy, expansive stadium sizzle (Cutoff ~9500 Hz)
    const cutoff = 2800 + (this.depth * 6700);
    if (this.toneFilter) {
      this.toneFilter.frequency.setTargetAtTime(cutoff, now, 0.02);
    }

    this.dryGain.gain.setTargetAtTime(effectiveDry, now, 0.02);
    this.wetGain.gain.setTargetAtTime(effectiveWet, now, 0.02);
  }

  resumeIfNeeded() {
    if (this.audioCtx && this.audioCtx.state === "suspended") {
      this.audioCtx.resume().catch(() => {});
    }
  }

  setEnabled(enabled) {
    this.isEnabled = Boolean(enabled);
    if (this.isEnabled) {
      this.ensureInitialized();
      this.resumeIfNeeded();
    }
    this.applyParameters();
  }

  setXY(xNorm, yNorm) {
    this.dryWet = Math.max(0, Math.min(1, xNorm));
    this.depth = Math.max(0, Math.min(1, yNorm));
    this.applyParameters();
  }
}

class NimiyoMusicPlayer {
  constructor() {
    // Playback Engine
    this.audio = new Audio();
    this.audio.preload = "auto";

    // Library State
    this.allTracks = [];
    this.nimiyoTracks = [];
    this.artistsMap = new Map();
    this.albumsMap = new Map();
    this.artworkCache = new Map();

    // Active Playback State
    this.currentTrack = null;
    this.queue = [];
    this.queueIndex = -1;
    this.shuffledIndices = [];
    this.shufflePointer = -1;
    this.isPlaying = false;
    this.isShuffle = false;
    this.repeatMode = "off"; // 'off' | 'all' | 'one'
    this.volume = 1.0;
    this.isMuted = false;
    this.preMuteVolume = 1.0;
    this.playbackRate = 1.0;
    this.durationDisplayMode = "total"; // 'total' | 'remaining'
    this.activeTab = "nimiyo"; // 'nimiyo' | 'songs' | 'artists' | 'album'
    this.sortMode = "title_asc"; // 'title_asc' | 'title_desc' | 'artist_asc' | 'album_asc' | 'dur_desc' | 'dur_asc'
    this.searchQuery = "";

    // Sleep Timer State
    this.sleepTimerSecondsLeft = 0;
    this.sleepTimerInterval = null;
    this.sleepTimerMode = "off"; // 'off' | 'time' | 'end_of_track'

    // Lyrics State
    this.currentLyrics = "";
    this.parsedLrc = []; // Array of { time: number, text: string }
    this.activeLyricIndex = -1;
    this.isLyricsModeActive = false;
    this.activeIntegratedLyricIndex = -1;
    this.isUserScrollingLyrics = false;
    this.isProgrammaticScrollingLyrics = false;
    this.lyricsScrollDebounceTimer = null;
    this.lyricsScrollCooldownTimer = null;
    this.isFetchingLyrics = false;

    // Drilldown Navigation State
    this.selectedArtist = null;
    this.selectedAlbum = null;

    // Playlist State
    this.playlists = [];
    this.activePlaylistId = null;
    this.selectedPickerTrackIds = new Set();
    this.pickerActiveTab = "all";
    this.pickerSearchQuery = "";
    this.editingPlaylistId = null;
    this.targetTrackForPlaylist = null;
    this.isPlaylistSelectionMode = false;
    this.selectedPlaylistTrackIds = new Set();

    // Selection Mode State
    this.isSelectionMode = false;
    this.selectedTrackIds = new Set();
    this.currentSelectionTrackList = [];
    this.longPressTimer = null;
    this.isLongPressTriggered = false;

    // Visualizer / Audio Context
    this.visualizerInterval = null;

    // Permission State
    this.permissionGranted = true;
    this.isCompactMiniPlayer = false;

    // MixTools BPM & Reverb State
    this.bpmRate = 100;
    this.isReverbEnabled = false;
    this.reverbDryWet = 0.5;
    this.reverbDepth = 0.5;
    this.reverbEngine = null;

    // DOM Elements Cache
    this.elements = {};

    // Initialization
    this.init();
  }

  // -------------------------------------------------------------
  // Initializer & Setup
  // -------------------------------------------------------------
  async init() {
    this.cacheDomElements();
    this.loadPersistedPreferences();
    this.loadPlaylists();
    this.setupAudioListeners();
    this.setupMediaSession();
    this.setupUiEventListeners();
    this.setupNativeMediaBridge();
    this.setupAudioInterruption();

    // Check permission & scan audio library
    await this.checkPermissionAndRefresh();

    // Restore last track if available
    this.restoreLastTrack();
  }

  cacheDomElements() {
    this.elements = {
      // Containers & Panes
      tabPlayer: document.getElementById("tab-player"),
      playerNavContainer: document.querySelector(".player-nav-container"),
      playerSearchBar: document.querySelector(".player-search-bar"),
      playerTabBar: document.getElementById("playerTabBar"),
      playerTabIndicator: document.getElementById("playerTabIndicator"),
      searchInput: document.getElementById("musicSearchInput"),
      clearSearchBtn: document.getElementById("musicClearSearchBtn"),
      subHeaderTitle: document.getElementById("playerSubHeaderTitle"),
      backSubHeaderBtn: document.getElementById("playerBackSubHeaderBtn"),
      libraryStats: document.getElementById("playerLibraryStats"),
      reloadLibraryBtn: document.getElementById("playerReloadLibraryBtn"),
      shuffleAllBtn: document.getElementById("playerShuffleAllBtn"),
      sortBtn: document.getElementById("playerSortBtn"),
      sortLabel: document.getElementById("playerSortLabel"),

      // Permission Container
      permissionPrompt: document.getElementById("playerPermissionPrompt"),
      grantPermissionBtn: document.getElementById("grantAudioPermBtn"),

      // Pane bodies
      paneNimiyo: document.getElementById("player-pane-nimiyo"),
      paneSongs: document.getElementById("player-pane-songs"),
      paneArtists: document.getElementById("player-pane-artists"),
      paneAlbums: document.getElementById("player-pane-albums"),
      paneDrilldown: document.getElementById("player-pane-drilldown"),

      // Drilldown elements
      drilldownHeader: document.getElementById("drilldownHeader"),
      drilldownTitle: document.getElementById("drilldownTitle"),
      drilldownSubtitle: document.getElementById("drilldownSubtitle"),
      drilldownList: document.getElementById("drilldownList"),

      // Mini Player
      miniPlayer: document.getElementById("miniPlayer"),
      miniPlayerArt: document.getElementById("miniPlayerArt"),
      miniPlayerTitle: document.getElementById("miniPlayerTitle"),
      miniPlayerArtist: document.getElementById("miniPlayerArtist"),
      miniPlayerVisualizer: document.getElementById("miniPlayerVisualizer"),
      miniPlayerPlayBtn: document.getElementById("miniPlayerPlayBtn"),
      miniPlayerMinimizeBtn: document.getElementById("miniPlayerMinimizeBtn"),
      miniPlayerCloseBtn: document.getElementById("miniPlayerCloseBtn"),
      miniPlayerCompactPlayBtn: document.getElementById("miniPlayerCompactPlayBtn"),
      miniPlayerProgress: document.getElementById("miniPlayerProgress"),

      // Full Player Modal
      fullPlayerModal: document.getElementById("fullPlayerModal"),
      fullPlayerBackdropGlow: document.getElementById("fullPlayerBackdropGlow"),
      closeFullPlayerBtn: document.getElementById("closeFullPlayerBtn"),
      fullPlayerStage: document.getElementById("fullPlayerStage"),
      fullPlayerArtStage: document.getElementById("fullPlayerArtStage"),
      fullPlayerArtCard: document.getElementById("fullPlayerArtCard"),
      fullPlayerArt: document.getElementById("fullPlayerArt"),
      artLyricsHintBadge: document.getElementById("artLyricsHintBadge"),
      fullPlayerLyricsStage: document.getElementById("fullPlayerLyricsStage"),
      lyricsMiniTrackInfo: document.getElementById("lyricsMiniTrackInfo"),
      lyricsMiniArt: document.getElementById("lyricsMiniArt"),
      lyricsMiniTitle: document.getElementById("lyricsMiniTitle"),
      lyricsMiniArtist: document.getElementById("lyricsMiniArtist"),
      toggleLyricsCoverBtn: document.getElementById("toggleLyricsCoverBtn"),
      fullPlayerLyricsScroll: document.getElementById("fullPlayerLyricsScroll"),
      lyricsSeekPill: document.getElementById("lyricsSeekPill"),
      lyricsSeekTime: document.getElementById("lyricsSeekTime"),
      fullPlayerInfoSection: document.getElementById("fullPlayerInfoSection"),
      fullPlayerTitle: document.getElementById("fullPlayerTitle"),
      fullPlayerArtist: document.getElementById("fullPlayerArtist"),
      fullPlayerAlbum: document.getElementById("fullPlayerAlbum"),
      fullPlayerLyricsBadge: document.getElementById("fullPlayerLyricsBadge"),
      fullPlayerVisualizer: document.getElementById("fullPlayerVisualizer"),
      fullPlayerProgressBar: document.getElementById("fullPlayerProgressBar"),
      fullPlayerCurrentTime: document.getElementById("fullPlayerCurrentTime"),
      fullPlayerDuration: document.getElementById("fullPlayerDuration"),
      fullPlayerShuffleBtn: document.getElementById("fullPlayerShuffleBtn"),
      fullPlayerPrevBtn: document.getElementById("fullPlayerPrevBtn"),
      fullPlayerPlayBtn: document.getElementById("fullPlayerPlayBtn"),
      fullPlayerNextBtn: document.getElementById("fullPlayerNextBtn"),
      fullPlayerRepeatBtn: document.getElementById("fullPlayerRepeatBtn"),
      fullPlayerQueueBtn: document.getElementById("fullPlayerQueueBtn"),
      fullPlayerMoreBtn: document.getElementById("fullPlayerMoreBtn"),
      fullPlayerSleepTimerBtn: document.getElementById("fullPlayerSleepTimerBtn"),
      fullPlayerSleepTimerBadge: document.getElementById("fullPlayerSleepTimerBadge"),
      fullPlayerSpeedBtn: document.getElementById("fullPlayerSpeedBtn"),
      fullPlayerSpeedLabel: document.getElementById("fullPlayerSpeedLabel"),
      fullPlayerVolumeSlider: document.getElementById("fullPlayerVolumeSlider"),
      fullPlayerVolumeIconBtn: document.getElementById("fullPlayerVolumeIconBtn"),
      fullPlayerVolumeIcon: document.getElementById("fullPlayerVolumeIcon"),

      // Queue Modal
      queueModal: document.getElementById("musicQueueModal"),
      closeQueueModalBtn: document.getElementById("closeQueueModalBtn"),
      queueNowPlayingTitle: document.getElementById("queueNowPlayingTitle"),
      queueNowPlayingArtist: document.getElementById("queueNowPlayingArtist"),
      queueNowPlayingArt: document.getElementById("queueNowPlayingArt"),
      queueListContainer: document.getElementById("queueListContainer"),
      clearQueueBtn: document.getElementById("clearQueueBtn"),

      // Lyrics Modal
      lyricsModal: document.getElementById("musicLyricsModal"),
      closeLyricsModalBtn: document.getElementById("closeLyricsModalBtn"),
      lyricsModalTitle: document.getElementById("lyricsModalTitle"),
      lyricsModalArtist: document.getElementById("lyricsModalArtist"),
      lyricsContent: document.getElementById("lyricsContent"),

      // Edit Lyrics & Reset / Import Modal Elements
      openEditLyricsBtn: document.getElementById("openEditLyricsBtn"),
      editLyricsModal: document.getElementById("editLyricsModal"),
      closeEditLyricsModalBtn: document.getElementById("closeEditLyricsModalBtn"),
      cancelEditLyricsBtn: document.getElementById("cancelEditLyricsBtn"),
      saveEditLyricsBtn: document.getElementById("saveEditLyricsBtn"),
      downloadLyricsFileBtn: document.getElementById("downloadLyricsFileBtn"),
      editLyricsTextarea: document.getElementById("editLyricsTextarea"),
      editLyricsTrackTitle: document.getElementById("editLyricsTrackTitle"),
      refreshLyricsBtn: document.getElementById("refreshLyricsBtn"),
      importLyricsBtn: document.getElementById("importLyricsBtn"),
      importLrcFileInput: document.getElementById("importLrcFileInput"),
      resetLyricsModal: document.getElementById("resetLyricsModal"),
      closeResetLyricsModalBtn: document.getElementById("closeResetLyricsModalBtn"),
      resetLyricsTrackTitle: document.getElementById("resetLyricsTrackTitle"),
      resetLyricsDefaultBtn: document.getElementById("resetLyricsDefaultBtn"),
      resetLyricsLyricsifyBtn: document.getElementById("resetLyricsLyricsifyBtn"),
      resetLyricsImportBtn: document.getElementById("resetLyricsImportBtn"),

      // Sleep Timer Modal
      sleepTimerModal: document.getElementById("musicSleepTimerModal"),
      closeSleepTimerModalBtn: document.getElementById("closeSleepTimerModalBtn"),
      customTimerInput: document.getElementById("customTimerInput"),
      setCustomTimerBtn: document.getElementById("setCustomTimerBtn"),

      // MixTools Modal Elements
      speedModal: document.getElementById("musicSpeedModal"),
      closeSpeedModalBtn: document.getElementById("closeSpeedModalBtn"),
      resetAllMixToolsBtn: document.getElementById("resetAllMixToolsBtn"),
      slowedBpmSlider: document.getElementById("slowedBpmSlider"),
      slowedBpmValueLabel: document.getElementById("slowedBpmValueLabel"),
      resetSlowedBpmBtn: document.getElementById("resetSlowedBpmBtn"),
      reverbToggle: document.getElementById("reverbToggle"),
      reverbPadContainer: document.getElementById("reverbPadContainer"),
      reverbStatusLabel: document.getElementById("reverbStatusLabel"),
      resetReverbBtn: document.getElementById("resetReverbBtn"),
      reverbXyPad: document.getElementById("reverbXyPad"),
      reverbPadPuck: document.getElementById("reverbPadPuck"),

      // Song Info Modal
      songInfoModal: document.getElementById("musicSongInfoModal"),
      closeSongInfoModalBtn: document.getElementById("closeSongInfoModalBtn"),
      infoFieldTitle: document.getElementById("infoFieldTitle"),
      infoFieldArtist: document.getElementById("infoFieldArtist"),
      infoFieldAlbum: document.getElementById("infoFieldAlbum"),
      infoFieldDuration: document.getElementById("infoFieldDuration"),
      infoFieldSize: document.getElementById("infoFieldSize"),
      infoFieldFormat: document.getElementById("infoFieldFormat"),
      infoFieldPath: document.getElementById("infoFieldPath"),

      // Sort Modal
      sortModal: document.getElementById("musicSortModal"),
      closeSortModalBtn: document.getElementById("closeSortModalBtn"),

      // Playlist Elements
      openPlaylistBtn: document.getElementById("playerOpenPlaylistBtn"),
      playlistModal: document.getElementById("playlistModal"),
      playlistBackBtn: document.getElementById("playlistBackBtn"),
      playlistHeaderTitle: document.getElementById("playlistHeaderTitle"),
      playlistCreateBtn: document.getElementById("playlistCreateBtn"),
      playlistListView: document.getElementById("playlistListView"),
      playlistCountSubtitle: document.getElementById("playlistCountSubtitle"),
      playlistCardsGrid: document.getElementById("playlistCardsGrid"),
      playlistEmptyState: document.getElementById("playlistEmptyState"),
      playlistEmptyCreateBtn: document.getElementById("playlistEmptyCreateBtn"),
      playlistDetailView: document.getElementById("playlistDetailView"),
      playlistDetailCover: document.getElementById("playlistDetailCover"),
      playlistDetailName: document.getElementById("playlistDetailName"),
      playlistDetailMeta: document.getElementById("playlistDetailMeta"),
      playlistPlayAllBtn: document.getElementById("playlistPlayAllBtn"),
      playlistShuffleBtn: document.getElementById("playlistShuffleBtn"),
      playlistAddSongsBtn: document.getElementById("playlistAddSongsBtn"),
      playlistDeleteBtn: document.getElementById("playlistDeleteBtn"),
      playlistSelectBtn: document.getElementById("playlistSelectBtn"),
      playlistSelectionBar: document.getElementById("playlistSelectionBar"),
      playlistSelectAllCheckbox: document.getElementById("playlistSelectAllCheckbox"),
      playlistSelectionCountBadge: document.getElementById("playlistSelectionCountBadge"),
      playlistRemoveSelectedBtn: document.getElementById("playlistRemoveSelectedBtn"),
      playlistCancelSelectionBtn: document.getElementById("playlistCancelSelectionBtn"),
      playlistTracksList: document.getElementById("playlistTracksList"),
      playlistTracksEmpty: document.getElementById("playlistTracksEmpty"),
      playlistTracksEmptyAddBtn: document.getElementById("playlistTracksEmptyAddBtn"),

      // Song Picker Elements
      songPickerModal: document.getElementById("songPickerModal"),
      closeSongPickerBtn: document.getElementById("closeSongPickerBtn"),
      pickerSelectedCount: document.getElementById("pickerSelectedCount"),
      pickerSearchInput: document.getElementById("pickerSearchInput"),
      pickerClearSearchBtn: document.getElementById("pickerClearSearchBtn"),
      pickerSelectAllBtn: document.getElementById("pickerSelectAllBtn"),
      pickerTotalTracksCount: document.getElementById("pickerTotalTracksCount"),
      pickerTrackList: document.getElementById("pickerTrackList"),
      pickerCancelBtn: document.getElementById("pickerCancelBtn"),
      pickerConfirmBtn: document.getElementById("pickerConfirmBtn"),

      // Create Playlist Dialog Elements
      createPlaylistModal: document.getElementById("createPlaylistModal"),
      createPlaylistDialogTitle: document.getElementById("createPlaylistDialogTitle"),
      closeCreatePlaylistBtn: document.getElementById("closeCreatePlaylistBtn"),
      newPlaylistNameInput: document.getElementById("newPlaylistNameInput"),
      cancelCreatePlaylistBtn: document.getElementById("cancelCreatePlaylistBtn"),
      submitCreatePlaylistBtn: document.getElementById("submitCreatePlaylistBtn"),

      // Add to Playlist Modal (from track context menu)
      addToPlaylistModal: document.getElementById("addToPlaylistModal"),
      closeAddToPlaylistBtn: document.getElementById("closeAddToPlaylistBtn"),
      addToPlaylistTrackTitle: document.getElementById("addToPlaylistTrackTitle"),
      quickCreatePlaylistBtn: document.getElementById("quickCreatePlaylistBtn"),
      quickPlaylistOptionsList: document.getElementById("quickPlaylistOptionsList"),

      // Selection Mode Elements
      selectionActionBar: document.getElementById("selectionActionBar"),
      selectionAllCheckbox: document.getElementById("selectionAllCheckbox"),
      selectionCountBadge: document.getElementById("selectionCountBadge"),
      selectionMoveBtn: document.getElementById("selectionMoveBtn"),
      selectionDeleteBtn: document.getElementById("selectionDeleteBtn"),
      selectionCancelBtn: document.getElementById("selectionCancelBtn"),

      // Move Target Modal Elements
      moveTargetModal: document.getElementById("moveTargetModal"),
      closeMoveTargetBtn: document.getElementById("closeMoveTargetBtn"),
      moveOptionPlaylistBtn: document.getElementById("moveOptionPlaylistBtn"),
      moveOptionNimiyoBtn: document.getElementById("moveOptionNimiyoBtn"),

      // Delete Confirm Modal Elements
      deleteConfirmModal: document.getElementById("deleteConfirmModal"),
      closeDeleteModalBtn: document.getElementById("closeDeleteModalBtn"),
      cancelDeleteModalBtn: document.getElementById("cancelDeleteModalBtn"),
      confirmDeleteModalBtn: document.getElementById("confirmDeleteModalBtn"),
      deleteConfirmMessage: document.getElementById("deleteConfirmMessage")
    };
  }

  loadPersistedPreferences() {
    try {
      const raw = localStorage.getItem("nimiyo_music_prefs");
      if (raw) {
        const prefs = JSON.parse(raw);
        if (typeof prefs.volume === "number") this.volume = prefs.volume;
        if (typeof prefs.isShuffle === "boolean") this.isShuffle = prefs.isShuffle;
        if (prefs.repeatMode && ["off", "all", "one"].includes(prefs.repeatMode)) {
          this.repeatMode = prefs.repeatMode;
        }
        if (prefs.sortMode) this.sortMode = prefs.sortMode;
        if (prefs.durationDisplayMode) this.durationDisplayMode = prefs.durationDisplayMode;
        if (typeof prefs.bpmRate === "number") this.bpmRate = prefs.bpmRate;
        else if (typeof prefs.slowedBpm === "number") this.bpmRate = prefs.slowedBpm;
        if (typeof prefs.playbackRate === "number" && !prefs.bpmRate) this.bpmRate = Math.round(prefs.playbackRate * 100);
        if (typeof prefs.isReverbEnabled === "boolean") this.isReverbEnabled = prefs.isReverbEnabled;
        if (typeof prefs.reverbDryWet === "number") this.reverbDryWet = prefs.reverbDryWet;
        if (typeof prefs.reverbDepth === "number") this.reverbDepth = prefs.reverbDepth;
        this.lastTrackId = prefs.lastTrackId || null;
      }
      this.audio.volume = this.volume;
      this.setBpmRate(this.bpmRate, false);
      if (this.elements.fullPlayerVolumeSlider) {
        this.elements.fullPlayerVolumeSlider.value = Math.round(this.volume * 100);
      }
      if (this.isReverbEnabled) {
        this.toggleReverbMode(true);
      }
      this.updateControlStatesUi();
      this.updateSortLabelUi();
    } catch (e) {
      console.warn("[MUSIC] Failed to load preferences:", e);
    }
  }

  savePreferences() {
    try {
      const prefs = {
        volume: this.volume,
        isShuffle: this.isShuffle,
        repeatMode: this.repeatMode,
        sortMode: this.sortMode,
        durationDisplayMode: this.durationDisplayMode,
        playbackRate: this.playbackRate,
        bpmRate: this.bpmRate,
        isReverbEnabled: this.isReverbEnabled,
        reverbDryWet: this.reverbDryWet,
        reverbDepth: this.reverbDepth,
        lastTrackId: this.currentTrack ? this.currentTrack.id : this.lastTrackId
      };
      localStorage.setItem("nimiyo_music_prefs", JSON.stringify(prefs));
    } catch (_) {}
  }

  // -------------------------------------------------------------
  // Permission & Library Scanning
  // -------------------------------------------------------------
  async checkPermissionAndRefresh() {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.getAudioPermissionStatus === "function") {
      try {
        const res = await MediaSaver.getAudioPermissionStatus();
        if (res && res.granted === false) {
          this.permissionGranted = false;
          this.showPermissionPrompt(res.status === "permanently_denied");
          return;
        }
      } catch (_) {}
    }

    this.permissionGranted = true;
    this.hidePermissionPrompt();
    await this.refreshLibrary();
  }

  async autoCheckPermissionOnResume() {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.getAudioPermissionStatus === "function") {
      try {
        const res = await MediaSaver.getAudioPermissionStatus();
        if (res && res.granted) {
          const wasNotGranted = !this.permissionGranted || this.allTracks.length === 0;
          this.permissionGranted = true;
          this.hidePermissionPrompt();
          if (wasNotGranted) {
            console.log("[MUSIC] Permission now granted, auto-refreshing library!");
            await this.refreshLibrary();
            if (window.showToast) {
              window.showToast(this.t("musicPermGranted", "Akses musik diizinkan, memuat lagu..."), "success");
            }
          }
        } else if (res && res.granted === false) {
          this.permissionGranted = false;
          this.showPermissionPrompt(res.status === "permanently_denied");
        }
      } catch (_) {}
    }
  }

  async requestAudioPermission() {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.checkAndRequestAudioPermission === "function") {
      try {
        const res = await MediaSaver.checkAndRequestAudioPermission();
        if (res && res.granted) {
          this.permissionGranted = true;
          this.hidePermissionPrompt();
          await this.refreshLibrary();
          if (window.showToast) {
            window.showToast(this.t("musicPermGranted", "Akses musik diizinkan, memuat lagu..."), "success");
          }
        } else if (res && res.status === "permanently_denied") {
          this.showPermissionPrompt(true);
          if (typeof MediaSaver.openAppSettings === "function") {
            MediaSaver.openAppSettings();
          }
        }
      } catch (e) {
        console.warn("[MUSIC] Request permission error:", e);
      }
    } else {
      await this.refreshLibrary();
    }
  }

  showPermissionPrompt(isPermanent = false) {
    if (this.elements.permissionPrompt) {
      this.elements.permissionPrompt.classList.remove("hidden");
      const btn = this.elements.grantPermissionBtn;
      if (btn) {
        btn.innerText = isPermanent
          ? this.t("btnOpenSettings", "Buka Pengaturan")
          : this.t("btnAllowAudioAccess", "Izinkan Akses Musik");
      }
    }
  }

  hidePermissionPrompt() {
    if (this.elements.permissionPrompt) {
      this.elements.permissionPrompt.classList.add("hidden");
    }
  }

  async refreshLibrary() {
    const reloadIcon = this.elements.reloadLibraryBtn;
    if (reloadIcon) reloadIcon.classList.add("spinning");

    try {
      let tracks = [];
      const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;

      if (MediaSaver && typeof MediaSaver.scanAudioLibrary === "function") {
        const res = await MediaSaver.scanAudioLibrary();
        if (res && Array.isArray(res.tracks)) {
          tracks = res.tracks;
        }
      }

      // If on browser or empty native result, check downloaded history files
      if (tracks.length === 0) {
        tracks = this.getTracksFromHistoryDownloads();
      }

      this.processLibraryTracks(tracks);
      this.renderCurrentTab();
      this.updateLibraryStatsUi();
    } catch (err) {
      console.error("[MUSIC] Library scan error:", err);
      if (window.showToast) {
        window.showToast(this.t("musicScanError", "Gagal memindai audio lokal"), "error");
      }
    } finally {
      if (reloadIcon) {
        setTimeout(() => reloadIcon.classList.remove("spinning"), 400);
      }
    }
  }

  getTracksFromHistoryDownloads() {
    const list = [];
    try {
      const historyRaw = localStorage.getItem("nimiyo_download_history_v2");
      if (historyRaw) {
        const items = JSON.parse(historyRaw);
        items.forEach((item, idx) => {
          const type = (item.fileType || "").toLowerCase();
          const name = (item.fileName || "").toLowerCase();
          if (type.includes("audio") || name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".flac")) {
            list.push({
              id: "history_" + (item.id || idx),
              title: item.title || item.fileName?.replace(/\.[^.]+$/, "") || `Track ${idx + 1}`,
              artist: item.author || item.artist || "",
              album: "",
              albumArtist: "",
              duration: 0,
              fileName: item.fileName || "audio.mp3",
              filePath: item.filePath || "",
              contentUri: item.contentUri || item.fileUri || item.downloadUrl || "",
              isNimiyo: true,
              hasArtwork: false,
              hasLyrics: false,
              mimeType: "audio/mpeg"
            });
          }
        });
      }
    } catch (_) {}
    return list;
  }

  processLibraryTracks(tracks) {
    this.allTracks = tracks || [];
    this.nimiyoTracks = [];
    this.artistsMap.clear();
    this.albumsMap.clear();

    for (const track of this.allTracks) {
      // Clean fallback strings
      const rawTitle = track.title || track.fileName?.replace(/\.[^.]+$/, "") || "";
      const rawArtist = track.artist && track.artist.trim() ? track.artist.trim() : "";
      const cleaned = this.cleanTitleAndArtist(rawTitle, rawArtist);

      const title = cleaned.title || rawTitle || this.t("musicUnknownTitle", "Untitled");
      const artist = cleaned.artist || rawArtist || this.t("musicUnknownArtist", "Unknown Artist");
      const album = track.album && track.album.trim() ? track.album.trim() : this.t("musicUnknownAlbum", "Unknown Album");

      track.displayTitle = title;
      track.displayArtist = artist;
      track.displayAlbum = album;

      // Filter NIMIYO folder tracks
      if (track.isNimiyo || (track.filePath && track.filePath.toLowerCase().includes("nimiyo"))) {
        this.nimiyoTracks.push(track);
      }

      // Group by Artist
      if (!this.artistsMap.has(artist)) {
        this.artistsMap.set(artist, {
          name: artist,
          tracks: [],
          albums: new Set(),
          artworkTrack: track
        });
      }
      const artistObj = this.artistsMap.get(artist);
      artistObj.tracks.push(track);
      if (album && album !== this.t("musicUnknownAlbum", "Unknown Album")) {
        artistObj.albums.add(album);
      }

      // Group by Album
      const albumKey = `${album}___${artist}`;
      if (!this.albumsMap.has(albumKey)) {
        this.albumsMap.set(albumKey, {
          title: album,
          artist: artist,
          year: track.year || "",
          tracks: [],
          artworkTrack: track
        });
      }
      this.albumsMap.get(albumKey).tracks.push(track);
    }

    // Sort tracks in albums by trackNumber
    this.albumsMap.forEach(alb => {
      alb.tracks.sort((a, b) => (a.trackNumber || 0) - (b.trackNumber || 0));
    });
  }

  restoreLastTrack() {
    if (this.lastTrackId && this.allTracks.length > 0) {
      const found = this.allTracks.find(t => t.id === this.lastTrackId);
      if (found) {
        this.setTrack(found, false);
      }
    }
  }

  // -------------------------------------------------------------
  // Sorting & Filtering Engine
  // -------------------------------------------------------------
  sortTracks(tracks) {
    if (!Array.isArray(tracks)) return [];
    const list = [...tracks];

    switch (this.sortMode) {
      case "title_asc":
        list.sort((a, b) => (a.displayTitle || "").localeCompare(b.displayTitle || ""));
        break;
      case "title_desc":
        list.sort((a, b) => (b.displayTitle || "").localeCompare(a.displayTitle || ""));
        break;
      case "artist_asc":
        list.sort((a, b) => (a.displayArtist || "").localeCompare(b.displayArtist || ""));
        break;
      case "album_asc":
        list.sort((a, b) => (a.displayAlbum || "").localeCompare(b.displayAlbum || ""));
        break;
      case "dur_desc":
        list.sort((a, b) => (b.duration || 0) - (a.duration || 0));
        break;
      case "dur_asc":
        list.sort((a, b) => (a.duration || 0) - (b.duration || 0));
        break;
      default:
        list.sort((a, b) => (a.displayTitle || "").localeCompare(b.displayTitle || ""));
        break;
    }
    return list;
  }

  setSortMode(mode) {
    this.sortMode = mode;
    this.savePreferences();
    this.updateSortLabelUi();
    this.renderCurrentTab();
    this.closeSortModal();
    if (window.showToast) {
      window.showToast(this.getSortModeLabel(mode), "info");
    }
  }

  getSortModeLabel(mode) {
    switch (mode) {
      case "title_asc": return this.t("musicSortTitleAsc", "Judul (A-Z)");
      case "title_desc": return this.t("musicSortTitleDesc", "Judul (Z-A)");
      case "artist_asc": return this.t("musicSortArtistAsc", "Artis (A-Z)");
      case "album_asc": return this.t("musicSortAlbumAsc", "Album (A-Z)");
      case "dur_desc": return this.t("musicSortDurDesc", "Durasi (Terpanjang)");
      case "dur_asc": return this.t("musicSortDurAsc", "Durasi (Terpendek)");
      default: return this.t("musicSortTitle", "Urutkan");
    }
  }

  updateSortLabelUi() {
    if (this.elements.sortLabel) {
      this.elements.sortLabel.innerText = this.getSortModeLabel(this.sortMode);
    }
    if (this.elements.sortModal) {
      this.elements.sortModal.querySelectorAll(".sort-option-item").forEach(item => {
        item.classList.toggle("active", item.getAttribute("data-sort") === this.sortMode);
      });
    }
  }

  // -------------------------------------------------------------
  // Audio Playback Engine & Listeners
  // -------------------------------------------------------------
  setupAudioListeners() {
    this.audio.addEventListener("timeupdate", () => {
      const cur = this.audio.currentTime || 0;
      const dur = this.audio.duration || (this.currentTrack?.duration ? this.currentTrack.duration / 1000 : 0);
      this.updateProgressUi(cur, dur);
      this.syncActiveLyric(cur);
      this.syncIntegratedLyrics(cur);
    });

    this.audio.addEventListener("loadedmetadata", () => {
      const dur = this.audio.duration || 0;
      if (dur > 0 && this.currentTrack) {
        this.currentTrack.duration = Math.round(dur * 1000);
        this.updateNativeNotification(this.isPlaying);
      }
      this.updateProgressUi(this.audio.currentTime || 0, dur);
    });

    this.audio.addEventListener("play", () => {
      this.isPlaying = true;
      if (this.bpmRate !== 100) {
        this.setPreservesPitch(false);
      } else {
        this.setPreservesPitch(true);
      }
      this.audio.playbackRate = this.bpmRate / 100;
      if (this.isReverbEnabled) {
        if (!this.reverbEngine) {
          this.reverbEngine = new NimiyoReverbFX(this.audio);
        }
        this.reverbEngine.setEnabled(true);
        this.reverbEngine.setXY(this.reverbDryWet, this.reverbDepth);
        this.reverbEngine.resumeIfNeeded();
      }
      this.updatePlaybackUiState(true);
      this.startVisualizer();
      this.updateMediaSessionState("playing");
      this.updateNativeNotification(true);
    });

    this.audio.addEventListener("playing", () => {
      if (this.isReverbEnabled) {
        if (!this.reverbEngine) {
          this.reverbEngine = new NimiyoReverbFX(this.audio);
        }
        this.reverbEngine.setEnabled(true);
        this.reverbEngine.setXY(this.reverbDryWet, this.reverbDepth);
        this.reverbEngine.resumeIfNeeded();
      }
    });

    this.audio.addEventListener("pause", () => {
      if (this.isStoppingPlayback) return;
      // If audio paused naturally because it reached the end of the track, do NOT send
      // a false PAUSE notification to Android, avoiding sticking at the final second before next track!
      const isEnding = this.audio.ended || (this.audio.duration && Math.abs(this.audio.duration - this.audio.currentTime) < 0.6);
      if (isEnding) return;

      this.isPlaying = false;
      this.updatePlaybackUiState(false);
      this.stopVisualizer();
      this.updateMediaSessionState("paused");
      this.updateNativeNotification(false);
    });

    this.audio.addEventListener("ended", () => {
      this.handleTrackEnded();
    });

    this.audio.addEventListener("error", (e) => {
      console.warn("[MUSIC] Audio playback error:", e, this.audio.error);
      this.isPlaying = false;
      this.updatePlaybackUiState(false);
      this.stopVisualizer();
      if (window.showToast) {
        window.showToast(this.t("musicPlaybackError", "Format audio tidak didukung atau file telah dipindah"), "error");
      }
    });
  }

  setupMediaSession() {
    if ("mediaSession" in navigator) {
      navigator.mediaSession.setActionHandler("play", () => this.play());
      navigator.mediaSession.setActionHandler("pause", () => this.pause());
      navigator.mediaSession.setActionHandler("previoustrack", () => this.previous());
      navigator.mediaSession.setActionHandler("nexttrack", () => this.next());
      navigator.mediaSession.setActionHandler("seekto", (details) => {
        if (details.seekTime !== undefined) this.seek(details.seekTime);
      });
      navigator.mediaSession.setActionHandler("seekbackward", (details) => {
        const skipTime = details.seekOffset || 10;
        this.seek(Math.max((this.audio.currentTime || 0) - skipTime, 0));
      });
      navigator.mediaSession.setActionHandler("seekforward", (details) => {
        const skipTime = details.seekOffset || 10;
        this.seek(Math.min((this.audio.currentTime || 0) + skipTime, this.audio.duration || 0));
      });
      navigator.mediaSession.setActionHandler("stop", () => this.pause());
    }
  }

  setupNativeMediaBridge() {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.addListener === "function") {
      MediaSaver.addListener("onMusicMediaAction", (data) => {
        if (data && data.action) {
          this.handleNativeMediaAction(data.action, data);
        }
      });
    }
  }

  handleNativeMediaAction(action, data) {
    switch (action) {
      case "playPause":
        this.togglePlay();
        break;
      case "play":
        this.play();
        break;
      case "pause":
        this.pause();
        break;
      case "next":
        this.next();
        break;
      case "prev":
      case "previous":
        this.previous();
        break;
      case "seek":
        if (data && typeof data.position === "number") {
          this.seek(data.position);
        }
        break;
    }
  }

  setupAudioInterruption() {
    if (navigator.mediaDevices && typeof navigator.mediaDevices.addEventListener === "function") {
      navigator.mediaDevices.addEventListener("devicechange", () => {
        if (this.isPlaying) {
          this.pause();
        }
      });
    }

    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) {
        if (this.currentTrack) {
          this.updatePlaybackUiState(this.isPlaying);
        }
        this.autoCheckPermissionOnResume();
      }
    });

    window.addEventListener("focus", () => {
      this.autoCheckPermissionOnResume();
    });

    const AppPlugin = window.Capacitor?.Plugins?.App;
    if (AppPlugin && typeof AppPlugin.addListener === "function") {
      AppPlugin.addListener("appStateChange", ({ isActive }) => {
        if (isActive) this.autoCheckPermissionOnResume();
      });
      AppPlugin.addListener("resume", () => {
        this.autoCheckPermissionOnResume();
      });
    }
  }

  // -------------------------------------------------------------
  // Track Playback Controls
  // -------------------------------------------------------------
  async setTrack(track, autoPlay = true) {
    if (!track) return;
    this.currentTrack = track;
    this.savePreferences();

    // Determine audio source
    let sourceUrl = track.contentUri || track.fileUri || track.filePath;

    if (window.Capacitor?.convertFileSrc && track.filePath) {
      try {
        sourceUrl = window.Capacitor.convertFileSrc(track.filePath);
      } catch (_) {}
    }

    this.audio.src = sourceUrl;
    if (this.bpmRate !== 100) {
      this.setPreservesPitch(false);
    } else {
      this.setPreservesPitch(true);
    }
    this.audio.playbackRate = this.bpmRate / 100;
    this.audio.load();

    // Immediately reset lyrics scroll & state to top on new song
    this.activeLyricIndex = -1;
    this.activeIntegratedLyricIndex = -1;
    this.isUserScrollingLyrics = false;
    if (this.elements.fullPlayerLyricsScroll) {
      this.elements.fullPlayerLyricsScroll.scrollTop = 0;
    }
    if (this.elements.lyricsContent) {
      this.elements.lyricsContent.scrollTop = 0;
    }

    // Refresh lyrics stage state if active
    if (this.isLyricsModeActive) {
      this.elements.fullPlayerLyricsStage?.classList.remove("hidden");
      this.elements.fullPlayerArtStage?.classList.add("hidden");
      this.elements.fullPlayerInfoSection?.classList.add("hidden");
    } else {
      this.elements.fullPlayerLyricsStage?.classList.add("hidden");
      this.elements.fullPlayerArtStage?.classList.remove("hidden");
      this.elements.fullPlayerInfoSection?.classList.remove("hidden");
    }

    this.updateTrackMetadataUi(track);
    this.updateMediaSessionMetadata(track);
    this.isPlaying = Boolean(autoPlay);
    // Immediately flip Android notification to the new track at 00:00
    this.updateNativeNotification(this.isPlaying, 0);
    this.fetchAndApplyArtwork(track);
    this.fetchLyrics(track);

    if (this.isReverbEnabled) {
      if (!this.reverbEngine) {
        this.reverbEngine = new NimiyoReverbFX(this.audio);
      }
      this.reverbEngine.setEnabled(true);
      this.reverbEngine.setXY(this.reverbDryWet, this.reverbDepth);
      this.reverbEngine.resumeIfNeeded();
    }

    if (autoPlay) {
      try {
        await this.audio.play();
      } catch (err) {
        console.warn("[MUSIC] Auto-play was prevented:", err);
      }
    }
  }

  async playTrackFromList(track, trackList = []) {
    if (!track) return;

    if (this.currentTrack && this.currentTrack.id === track.id) {
      this.togglePlay();
      return;
    }

    if (trackList && trackList.length > 0) {
      this.queue = [...trackList];
      this.queueIndex = this.queue.findIndex(t => t.id === track.id);
      if (this.queueIndex === -1) {
        this.queue.unshift(track);
        this.queueIndex = 0;
      }
    } else {
      if (this.queue.length === 0) {
        this.queue = [track];
        this.queueIndex = 0;
      } else {
        this.queueIndex = this.queue.findIndex(t => t.id === track.id);
        if (this.queueIndex === -1) {
          this.queue.push(track);
          this.queueIndex = this.queue.length - 1;
        }
      }
    }

    this.rebuildShuffleIndices();
    await this.setTrack(track, true);
    this.showMiniPlayer();
  }

  async shuffleAll(tracks = null) {
    let list = tracks;
    if (!list || list.length === 0) {
      list = this.activeTab === "nimiyo" ? this.nimiyoTracks : this.allTracks;
    }
    if (!list || list.length === 0) {
      if (window.showToast) {
        window.showToast(this.t("musicNoSongs", "Tidak ada file audio"), "info");
      }
      return;
    }

    // Enable shuffle mode
    this.isShuffle = true;
    this.queue = [...list];
    this.rebuildShuffleIndices();
    this.queueIndex = this.shuffledIndices[0] || 0;
    this.shufflePointer = 0;

    this.updateControlStatesUi();
    await this.setTrack(this.queue[this.queueIndex], true);
    this.showMiniPlayer();

    if (window.showToast) {
      window.showToast(this.t("musicShuffleAllStarted", "Memutar acak semua lagu"), "info");
    }
  }

  async play() {
    if (!this.currentTrack && this.queue.length > 0) {
      await this.setTrack(this.queue[0], true);
      return;
    }
    if (this.audio.src) {
      try {
        await this.audio.play();
      } catch (err) {
        console.warn("[MUSIC] play() error:", err);
      }
    }
  }

  pause() {
    this.audio.pause();
  }

  togglePlay() {
    if (this.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  next() {
    if (this.queue.length === 0) return;

    if (this.isShuffle) {
      this.shufflePointer++;
      if (this.shufflePointer >= this.shuffledIndices.length) {
        if (this.repeatMode === "all") {
          this.rebuildShuffleIndices();
          this.shufflePointer = 0;
        } else {
          this.pause();
          return;
        }
      }
      this.queueIndex = this.shuffledIndices[this.shufflePointer];
    } else {
      this.queueIndex++;
      if (this.queueIndex >= this.queue.length) {
        if (this.repeatMode === "all") {
          this.queueIndex = 0;
        } else {
          this.pause();
          return;
        }
      }
    }

    const nextTrack = this.queue[this.queueIndex];
    if (nextTrack) {
      this.setTrack(nextTrack, true);
    }
  }

  previous() {
    if (this.queue.length === 0) return;

    // If played more than 3 seconds, restart current track
    if (this.audio.currentTime > 3) {
      this.seek(0);
      return;
    }

    if (this.isShuffle) {
      this.shufflePointer = Math.max(0, this.shufflePointer - 1);
      this.queueIndex = this.shuffledIndices[this.shufflePointer] || 0;
    } else {
      this.queueIndex--;
      if (this.queueIndex < 0) {
        this.queueIndex = this.repeatMode === "all" ? this.queue.length - 1 : 0;
      }
    }

    const prevTrack = this.queue[this.queueIndex];
    if (prevTrack) {
      this.setTrack(prevTrack, true);
    }
  }

  seek(seconds) {
    if (!isNaN(seconds) && isFinite(seconds)) {
      this.audio.currentTime = seconds;
      this.updateNativeNotification(this.isPlaying);
    }
  }

  setVolume(val) {
    this.volume = Math.max(0, Math.min(1, val));
    this.isMuted = this.volume === 0;
    this.audio.volume = this.volume;
    this.savePreferences();
    this.updateVolumeUi();
  }

  toggleMute() {
    if (this.isMuted || this.volume === 0) {
      this.setVolume(this.preMuteVolume || 0.8);
      this.isMuted = false;
    } else {
      this.preMuteVolume = this.volume;
      this.setVolume(0);
      this.isMuted = true;
    }
  }

  updateVolumeUi() {
    if (this.elements.fullPlayerVolumeSlider) {
      const volPct = Math.round(this.volume * 100);
      this.elements.fullPlayerVolumeSlider.value = volPct;
      this.elements.fullPlayerVolumeSlider.style.setProperty("--progress", `${volPct}%`);
    }
    if (this.elements.fullPlayerVolumeIcon) {
      if (this.volume === 0 || this.isMuted) {
        this.elements.fullPlayerVolumeIcon.innerHTML = `
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon>
          <line x1="23" y1="9" x2="17" y2="15"></line>
          <line x1="17" y1="9" x2="23" y2="15"></line>
        `;
      } else if (this.volume < 0.5) {
        this.elements.fullPlayerVolumeIcon.innerHTML = `
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon>
          <path d="M15.54 8.46a5 5 0 0 1 0 7.07"></path>
        `;
      } else {
        this.elements.fullPlayerVolumeIcon.innerHTML = `
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon>
          <path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"></path>
        `;
      }
    }
  }

  setPreservesPitch(preserve) {
    try {
      if ("preservesPitch" in this.audio) {
        this.audio.preservesPitch = preserve;
      }
      if ("webkitPreservesPitch" in this.audio) {
        this.audio.webkitPreservesPitch = preserve;
      }
      if ("mozPreservesPitch" in this.audio) {
        this.audio.mozPreservesPitch = preserve;
      }
    } catch (_) {}
  }

  setBpmRate(bpm, shouldToast = false) {
    const clamped = Math.max(60, Math.min(140, bpm));
    this.bpmRate = clamped;
    const speed = clamped / 100;
    this.playbackRate = speed;

    if (clamped !== 100) {
      this.setPreservesPitch(false);
    } else {
      this.setPreservesPitch(true);
    }
    this.audio.playbackRate = speed;

    // Symmetrical range: min 60, max 140 -> 100 is EXACTLY at 50% (dead center)
    const pct = ((clamped - 60) / (140 - 60)) * 100;
    if (this.elements.slowedBpmSlider) {
      this.elements.slowedBpmSlider.style.setProperty("--progress", `${pct.toFixed(1)}%`);
      this.elements.slowedBpmSlider.value = clamped;
    }

    let tag = "Normal";
    if (clamped < 100) tag = "Slowed";
    else if (clamped > 100) tag = "SpeedUp";

    if (this.elements.slowedBpmValueLabel) {
      this.elements.slowedBpmValueLabel.innerText = `${clamped}% • ${tag}`;
    }
    if (this.elements.fullPlayerSpeedLabel) {
      this.elements.fullPlayerSpeedLabel.innerText = clamped === 100 ? "MixTools" : `${clamped}%`;
    }
    this.savePreferences();

    if (this.currentTrack) {
      this.updateNativeNotification(this.isPlaying);
    }

    if (shouldToast && window.showToast) {
      window.showToast(`BPM: ${clamped}% (${tag})`, "info");
    }
  }

  toggleReverbMode(enabled) {
    this.isReverbEnabled = Boolean(enabled);
    if (this.elements.reverbPadContainer) {
      this.elements.reverbPadContainer.classList.toggle("hidden", !this.isReverbEnabled);
    }
    if (this.isReverbEnabled) {
      if (!this.reverbEngine) {
        this.reverbEngine = new NimiyoReverbFX(this.audio);
      }
      this.reverbEngine.setEnabled(true);
      this.setReverbXY(this.reverbDryWet, this.reverbDepth);
      if (window.showToast) {
        window.showToast("Reverb FX Aktif", "info");
      }
    } else {
      if (this.reverbEngine) {
        this.reverbEngine.setEnabled(false);
      }
    }
    this.savePreferences();
  }

  setReverbXY(xNorm, yNorm) {
    this.reverbDryWet = Math.max(0, Math.min(1, xNorm));
    this.reverbDepth = Math.max(0, Math.min(1, yNorm));

    if (this.elements.reverbPadPuck) {
      this.elements.reverbPadPuck.style.left = `${(this.reverbDryWet * 100).toFixed(1)}%`;
      this.elements.reverbPadPuck.style.top = `${((1 - this.reverbDepth) * 100).toFixed(1)}%`;
    }

    if (this.elements.reverbStatusLabel) {
      this.elements.reverbStatusLabel.innerText = `Dry/Wet: ${Math.round(this.reverbDryWet * 100)}% • Decay: ${Math.round(this.reverbDepth * 100)}%`;
    }

    if (this.reverbEngine) {
      this.reverbEngine.setXY(this.reverbDryWet, this.reverbDepth);
    }
    this.savePreferences();
  }

  resetAllMixTools() {
    this.setBpmRate(100, false);
    this.toggleReverbMode(false);
    this.setReverbXY(0.5, 0.5);
    if (this.elements.reverbToggle) {
      this.elements.reverbToggle.checked = false;
    }
    if (this.elements.reverbPadContainer) {
      this.elements.reverbPadContainer.classList.add("hidden");
    }
    this.savePreferences();
    if (window.showToast) {
      window.showToast("MixTools di-reset ke default", "info");
    }
  }

  setupReverbPadEvents() {
    const pad = this.elements.reverbXyPad;
    if (!pad) return;

    let isDragging = false;

    const updateFromCoord = (clientX, clientY) => {
      const rect = pad.getBoundingClientRect();
      if (!rect.width || !rect.height) return;
      const x = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      const y = Math.max(0, Math.min(1, 1 - (clientY - rect.top) / rect.height));
      this.setReverbXY(x, y);
    };

    pad.addEventListener("pointerdown", (e) => {
      isDragging = true;
      try { pad.setPointerCapture(e.pointerId); } catch (_) {}
      updateFromCoord(e.clientX, e.clientY);
    });

    pad.addEventListener("pointermove", (e) => {
      if (isDragging) {
        updateFromCoord(e.clientX, e.clientY);
      }
    });

    const stopDragging = (e) => {
      if (isDragging) {
        isDragging = false;
        try { pad.releasePointerCapture(e.pointerId); } catch (_) {}
      }
    };

    pad.addEventListener("pointerup", stopDragging);
    pad.addEventListener("pointercancel", stopDragging);
  }

  toggleDurationDisplayMode() {
    this.durationDisplayMode = this.durationDisplayMode === "total" ? "remaining" : "total";
    this.savePreferences();
    const cur = this.audio.currentTime || 0;
    const dur = this.audio.duration || 0;
    this.updateProgressUi(cur, dur);
  }

  toggleShuffle() {
    this.isShuffle = !this.isShuffle;
    this.rebuildShuffleIndices();
    this.savePreferences();
    this.updateControlStatesUi();
    if (window.showToast) {
      window.showToast(this.isShuffle ? this.t("musicShuffleOn", "Shuffle Aktif") : this.t("musicShuffleOff", "Shuffle Nonaktif"), "info");
    }
  }

  cycleRepeat() {
    if (this.repeatMode === "off") this.repeatMode = "all";
    else if (this.repeatMode === "all") this.repeatMode = "one";
    else this.repeatMode = "off";

    this.savePreferences();
    this.updateControlStatesUi();

    let text = this.t("musicRepeatOff", "Repeat Nonaktif");
    if (this.repeatMode === "all") text = this.t("musicRepeatAll", "Ulangi Semua");
    else if (this.repeatMode === "one") text = this.t("musicRepeatOne", "Ulangi Satu Lagu");

    if (window.showToast) {
      window.showToast(text, "info");
    }
  }

  handleTrackEnded() {
    if (this.sleepTimerMode === "end_of_track") {
      this.setSleepTimer(0);
      this.pause();
      if (window.showToast) {
        window.showToast(this.t("musicSleepTimerFinished", "Sleep timer selesai"), "info");
      }
      return;
    }

    if (this.repeatMode === "one") {
      this.seek(0);
      this.play();
    } else {
      this.next();
    }
  }

  rebuildShuffleIndices() {
    const len = this.queue.length;
    this.shuffledIndices = Array.from({ length: len }, (_, i) => i);
    for (let i = len - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [this.shuffledIndices[i], this.shuffledIndices[j]] = [this.shuffledIndices[j], this.shuffledIndices[i]];
    }
    this.shufflePointer = this.shuffledIndices.indexOf(this.queueIndex);
    if (this.shufflePointer === -1) this.shufflePointer = 0;
  }

  // -------------------------------------------------------------
  // Sleep Timer Engine
  // -------------------------------------------------------------
  setSleepTimer(option) {
    if (this.sleepTimerInterval) {
      clearInterval(this.sleepTimerInterval);
      this.sleepTimerInterval = null;
    }

    if (option === 0 || option === "0") {
      this.sleepTimerMode = "off";
      this.sleepTimerSecondsLeft = 0;
      this.updateSleepTimerBadge();
      this.closeSleepTimerModal();
      if (window.showToast) {
        window.showToast(this.t("musicTimerOffToast", "Sleep timer dinonaktifkan"), "info");
      }
      return;
    }

    if (option === "end_of_track") {
      this.sleepTimerMode = "end_of_track";
      this.sleepTimerSecondsLeft = 0;
      this.updateSleepTimerBadge();
      this.closeSleepTimerModal();
      if (window.showToast) {
        window.showToast(this.t("musicTimerEndSongToast", "Berhenti setelah lagu selesai"), "info");
      }
      return;
    }

    const mins = parseInt(option, 10);
    if (!isNaN(mins) && mins > 0) {
      this.sleepTimerMode = "time";
      this.sleepTimerSecondsLeft = mins * 60;
      this.updateSleepTimerBadge();
      this.closeSleepTimerModal();

      if (window.showToast) {
        window.showToast(`${this.t("musicSleepTimerSet", "Sleep timer diatur")}: ${mins} min`, "info");
      }

      this.sleepTimerInterval = setInterval(() => {
        this.sleepTimerSecondsLeft--;
        this.updateSleepTimerBadge();

        // Smooth fade out in the last 15 seconds
        if (this.sleepTimerSecondsLeft <= 15 && this.sleepTimerSecondsLeft > 0) {
          const fadeVol = (this.sleepTimerSecondsLeft / 15) * this.volume;
          this.audio.volume = Math.max(0, fadeVol);
        }

        if (this.sleepTimerSecondsLeft <= 0) {
          clearInterval(this.sleepTimerInterval);
          this.sleepTimerInterval = null;
          this.sleepTimerMode = "off";
          this.pause();
          this.audio.volume = this.volume; // restore volume
          this.updateSleepTimerBadge();
          if (window.showToast) {
            window.showToast(this.t("musicSleepTimerFinished", "Sleep timer selesai"), "info");
          }
        }
      }, 1000);
    }
  }

  updateSleepTimerBadge() {
    const badge = this.elements.fullPlayerSleepTimerBadge;
    if (!badge) return;

    if (this.sleepTimerMode === "off") {
      badge.classList.add("hidden");
      badge.innerText = "";
    } else if (this.sleepTimerMode === "end_of_track") {
      badge.classList.remove("hidden");
      badge.innerText = "1 Song";
    } else if (this.sleepTimerMode === "time") {
      badge.classList.remove("hidden");
      const m = Math.ceil(this.sleepTimerSecondsLeft / 60);
      badge.innerText = `${m}m`;
    }
  }

  // -------------------------------------------------------------
  // Queue Management
  // -------------------------------------------------------------
  addToQueue(track) {
    if (!track) return;
    this.queue.push(track);
    this.rebuildShuffleIndices();
    if (this.queue.length === 1 && !this.currentTrack) {
      this.setTrack(track, false);
    }
    if (window.showToast) {
      window.showToast(`${track.displayTitle} ${this.t("musicAddedToQueue", "ditambahkan ke antrean")}`, "success");
    }
    this.renderQueueModal();
  }

  playNext(track) {
    if (!track) return;
    if (this.queue.length === 0) {
      this.playTrackFromList(track, [track]);
      return;
    }
    const insertIdx = this.queueIndex + 1;
    this.queue.splice(insertIdx, 0, track);
    this.rebuildShuffleIndices();
    if (window.showToast) {
      window.showToast(`${track.displayTitle} ${this.t("musicWillPlayNext", "akan diputar berikutnya")}`, "success");
    }
    this.renderQueueModal();
  }

  removeFromQueue(index) {
    if (index < 0 || index >= this.queue.length) return;
    this.queue.splice(index, 1);
    if (index < this.queueIndex) {
      this.queueIndex--;
    } else if (index === this.queueIndex) {
      if (this.queue.length > 0) {
        this.queueIndex = Math.min(this.queueIndex, this.queue.length - 1);
        this.setTrack(this.queue[this.queueIndex], this.isPlaying);
      } else {
        this.currentTrack = null;
        this.pause();
        this.audio.src = "";
        this.hideMiniPlayer();
        this.clearNativeNotification();
      }
    }
    this.rebuildShuffleIndices();
    this.renderQueueModal();
  }

  clearQueue() {
    if (this.currentTrack) {
      this.queue = [this.currentTrack];
      this.queueIndex = 0;
    } else {
      this.queue = [];
      this.queueIndex = -1;
    }
    this.rebuildShuffleIndices();
    this.renderQueueModal();
    if (window.showToast) {
      window.showToast(this.t("musicQueueCleared", "Antrean dibersihkan"), "info");
    }
  }

  // -------------------------------------------------------------
  // Artwork & Dynamic Color Palette Extraction
  // -------------------------------------------------------------
  async fetchAndApplyArtwork(track) {
    if (!track) return;
    const defaultPlaceholder = this.getPlaceholderArtworkSvg();

    // Check memory cache
    if (this.artworkCache.has(track.id)) {
      const cached = this.artworkCache.get(track.id);
      this.applyArtworkToUi(cached || defaultPlaceholder);
      if (cached) this.extractDominantColor(cached);
      return;
    }

    // Try native extraction
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.getAudioArtwork === "function" && (track.filePath || track.contentUri)) {
      try {
        const res = await MediaSaver.getAudioArtwork({
          filePath: track.filePath || "",
          contentUri: track.contentUri || ""
        });
        if (res && res.hasArtwork && res.artwork) {
          this.artworkCache.set(track.id, res.artwork);
          this.applyArtworkToUi(res.artwork);
          this.extractDominantColor(res.artwork);
          this.updateNativeNotification(this.isPlaying);
          return;
        }
      } catch (_) {}
    }

    // Fallback placeholder
    this.artworkCache.set(track.id, null);
    this.applyArtworkToUi(defaultPlaceholder);
    this.resetDynamicColor();
  }

  applyArtworkToUi(artworkSrc) {
    if (this.elements.miniPlayerArt) this.elements.miniPlayerArt.src = artworkSrc;
    if (this.elements.fullPlayerArt) this.elements.fullPlayerArt.src = artworkSrc;
    if (this.elements.lyricsMiniArt) this.elements.lyricsMiniArt.src = artworkSrc;
    if (this.elements.queueNowPlayingArt) this.elements.queueNowPlayingArt.src = artworkSrc;
  }

  extractDominantColor(imageSrc) {
    if (!imageSrc || imageSrc.startsWith("data:image/svg")) {
      this.resetDynamicColor();
      return;
    }

    const img = new Image();
    img.crossOrigin = "Anonymous";
    img.onload = () => {
      try {
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");
        canvas.width = 16;
        canvas.height = 16;
        ctx.drawImage(img, 0, 0, 16, 16);
        const data = ctx.getImageData(0, 0, 16, 16).data;

        let r = 0, g = 0, b = 0, count = 0;
        for (let i = 0; i < data.length; i += 4) {
          const red = data[i];
          const green = data[i + 1];
          const blue = data[i + 2];
          const brightness = (red * 299 + green * 587 + blue * 114) / 1000;
          if (brightness > 30 && brightness < 220) {
            r += red;
            g += green;
            b += blue;
            count++;
          }
        }

        if (count > 0) {
          r = Math.round(r / count);
          g = Math.round(g / count);
          b = Math.round(b / count);
          const glowRgba = `rgba(${r}, ${g}, ${b}, 0.55)`;
          if (this.elements.fullPlayerBackdropGlow) {
            this.elements.fullPlayerBackdropGlow.style.background = `radial-gradient(circle at center, ${glowRgba} 0%, rgba(0,0,0,0) 75%)`;
          }
        } else {
          this.resetDynamicColor();
        }
      } catch (_) {
        this.resetDynamicColor();
      }
    };
    img.onerror = () => this.resetDynamicColor();
    img.src = imageSrc;
  }

  resetDynamicColor() {
    if (this.elements.fullPlayerBackdropGlow) {
      this.elements.fullPlayerBackdropGlow.style.background = `radial-gradient(circle at center, var(--accent-glow, rgba(255, 204, 0, 0.4)) 0%, rgba(0,0,0,0) 75%)`;
    }
  }

  getPlaceholderArtworkSvg() {
    return `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" fill="%23facc15"><rect width="100" height="100" fill="%231e293b"/><circle cx="50" cy="50" r="32" fill="%230f172a"/><path d="M44 62V38l20-4v24" stroke="%23facc15" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/><circle cx="40" cy="62" r="6" fill="%23facc15"/><circle cx="60" cy="58" r="6" fill="%23facc15"/></svg>`;
  }

  // -------------------------------------------------------------
  // Lyrics Engine (Online LRCLIB + Netease + Local Storage Cache + Embedded / Companion LRC)
  // -------------------------------------------------------------
  cleanTitleAndArtist(rawTitle, rawArtist) {
    let title = (rawTitle || "").trim();
    let artist = (rawArtist || "").trim();

    // 1. Remove file extensions
    title = title.replace(/\.(mp3|m4a|wav|ogg|flac|aac|opus|webm|mp4)$/i, "").trim();

    // 2. Replace multiple underscores with spaces
    if (title.includes("_")) {
      title = title.replace(/_+/g, " ").trim();
    }
    if (artist.includes("_")) {
      artist = artist.replace(/_+/g, " ").trim();
    }

    // 3. Remove trailing duplicate index numbers (e.g. " 6", " (1)", " 1")
    title = title.replace(/\s*\(?\d+\)?$/, "").trim();

    // 4. If artist is missing or unknown, check if title is "Artist - Title"
    const isUnknownArtist = !artist || /^(unknown|unknown artist|tanpa artis|<unknown>)$/i.test(artist);
    if (isUnknownArtist && title.includes(" - ")) {
      const parts = title.split(" - ");
      artist = parts[0].trim();
      title = parts.slice(1).join(" - ").trim();
    }

    // 5. Extract (feat. ...) from title if present
    const featMatch = title.match(/[\(\[\{]\s*(?:feat\.?|ft\.?)\s+([^()\[\]{}]+)[\)\]\}]/i);
    const featArtist = featMatch ? featMatch[1].trim() : "";

    // 6. Clean common YouTube/video/audio noise tags
    title = title
      .replace(/[\(\[\{][^\)\]\}]*(?:official|audio|video|lyric|lyrics|music video|mv|visualizer|hd|4k|hq|remaster|cover|live|clip|karaoke|full|version|prod|radio edit)[^\)\]\}]*[\)\]\}]/gi, "")
      .replace(/[\(\[\{]\s*(?:feat\.?|ft\.?)\s+[^()\[\]{}]+[\)\]\}]/gi, "")
      .replace(/\s*\|\s*.*$/g, "")
      .replace(/\s*-\s*.*(?:Official|Radio Edit).*$/gi, "")
      .replace(/\s{2,}/g, " ")
      .trim();

    // 7. Normalize contractions e.g. "Don t" -> "Don't"
    title = title.replace(/\b([Dd]on|[Cc]an|[Ww]on|[Ii]t|[Yy]ou|[Dd]oesn|[Ww]ouldn|[Ss]houldn)\s+([tsdremlv]{1,2})\b/g, "$1'$2");

    // 8. Primary artist (first before comma, &, /, or feat)
    let primaryArtist = artist;
    if (primaryArtist) {
      primaryArtist = primaryArtist.split(/[,&/]|(?:\s+feat\.?\s+)|(?:\s+ft\.?\s+)/i)[0].trim();
    }

    return { title, artist, primaryArtist, featArtist };
  }

  async requestLyricsHttp(url) {
    // 1. Try Native CapacitorHttp (bypasses CORS on Android)
    const CapHttp = window.Capacitor?.Plugins?.CapacitorHttp;
    if (CapHttp && typeof CapHttp.request === "function") {
      try {
        const resp = await CapHttp.request({
          method: "GET",
          url: url,
          headers: {
            "User-Agent": "NimiyoMusicPlayer/1.0",
            "Accept": "application/json"
          }
        });
        if (resp && resp.status >= 200 && resp.status < 300) {
          return typeof resp.data === "string" ? JSON.parse(resp.data) : resp.data;
        }
      } catch (err) {
        console.warn("[LYRICS] CapHttp request error:", err);
      }
    }

    // 2. Fetch fallback
    try {
      const res = await fetch(url, {
        headers: {
          "User-Agent": "NimiyoMusicPlayer/1.0",
          "Accept": "application/json"
        }
      });
      if (res.ok) {
        return await res.json();
      }
    } catch (err) {
      console.warn("[LYRICS] Fetch fallback error:", err);
    }
    return null;
  }

  async fetchOnlineLyrics(track) {
    const { title, artist, primaryArtist, featArtist } = this.cleanTitleAndArtist(track.displayTitle || track.title, track.displayArtist || track.artist);
    if (!title) return null;

    const trackDuration = Math.round(track.duration ? (track.duration > 1000 ? track.duration / 1000 : track.duration) : (this.audio.duration || 0));

    // Helper: evaluate and score candidate lyrics
    const pickBestLyrics = (list) => {
      if (!Array.isArray(list) || list.length === 0) return null;

      // 1. First priority: candidate with syncedLyrics
      const syncedCandidates = list.filter(item => item.syncedLyrics && typeof item.syncedLyrics === "string" && item.syncedLyrics.trim().length > 0);
      if (syncedCandidates.length > 0) {
        if (trackDuration > 15) {
          syncedCandidates.sort((a, b) => {
            const diffA = Math.abs((a.duration || 0) - trackDuration);
            const diffB = Math.abs((b.duration || 0) - trackDuration);
            return diffA - diffB;
          });
        }
        return syncedCandidates[0].syncedLyrics.trim();
      }

      // 2. Second priority: candidate with plainLyrics
      const plainCandidates = list.filter(item => item.plainLyrics && typeof item.plainLyrics === "string" && item.plainLyrics.trim().length > 0);
      if (plainCandidates.length > 0) {
        if (trackDuration > 15) {
          plainCandidates.sort((a, b) => {
            const diffA = Math.abs((a.duration || 0) - trackDuration);
            const diffB = Math.abs((b.duration || 0) - trackDuration);
            return diffA - diffB;
          });
        }
        return plainCandidates[0].plainLyrics.trim();
      }

      return null;
    };

    // Strategy 1: LRCLIB exact get with primaryArtist or artist
    const artistsToTry = [primaryArtist, artist, featArtist].filter(Boolean);
    for (const art of artistsToTry) {
      try {
        const params = new URLSearchParams();
        params.append("track_name", title);
        params.append("artist_name", art);
        const url = `https://lrclib.net/api/get?${params.toString()}`;
        const data = await this.requestLyricsHttp(url);
        if (data && data.syncedLyrics && data.syncedLyrics.trim().length > 0) {
          return data.syncedLyrics.trim();
        }
      } catch (_) {}
    }

    // Strategy 2: LRCLIB search queries (most specific to general)
    const queries = [];
    if (primaryArtist) queries.push(`${primaryArtist} ${title}`);
    if (artist && artist !== primaryArtist) queries.push(`${artist} ${title}`);
    if (featArtist) queries.push(`${title} ${featArtist}`);
    queries.push(title);

    for (const q of queries) {
      try {
        const url = `https://lrclib.net/api/search?q=${encodeURIComponent(q)}`;
        const list = await this.requestLyricsHttp(url);
        const best = pickBestLyrics(list);
        if (best) return best;
      } catch (_) {}
    }

    // Strategy 3: Netease Cloud Music fallback
    try {
      const q = (primaryArtist ? primaryArtist + " " : "") + title;
      const sUrl = `https://music.xianqiao.wang/neteaseapiv2/search?keywords=${encodeURIComponent(q)}&limit=5`;
      const sData = await this.requestLyricsHttp(sUrl);
      const songs = sData?.result?.songs || [];
      for (const song of songs) {
        if (song?.id) {
          const lyrUrl = `https://music.xianqiao.wang/neteaseapiv2/lyric?id=${song.id}`;
          const lyrData = await this.requestLyricsHttp(lyrUrl);
          const lrc = lyrData?.lrc?.lyric;
          if (lrc && lrc.trim() && /\[\d{1,2}:\d{2}/.test(lrc)) {
            return lrc.trim();
          }
        }
      }
    } catch (_) {}

    return null;
  }

  saveLyricsToCache(trackKey, lyrics) {
    try {
      localStorage.setItem(trackKey, lyrics);
    } catch (e) {
      try {
        const lrcKeys = [];
        for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i);
          if (k && k.startsWith("nimiyo_lrc_")) {
            lrcKeys.push(k);
          }
        }
        for (let i = 0; i < Math.max(10, Math.ceil(lrcKeys.length / 2)); i++) {
          if (lrcKeys[i]) localStorage.removeItem(lrcKeys[i]);
        }
        localStorage.setItem(trackKey, lyrics);
      } catch (_) {}
    }
  }

  async saveLyricsToStorage(track, lyrics, notify = false) {
    if (!track || !lyrics) return;
    const trackKey = `nimiyo_lrc_${track.id || track.filePath || (track.displayTitle + "_" + track.displayArtist)}`;
    this.saveLyricsToCache(trackKey, lyrics);

    // Save to physical storage directory Nimiyo/LyricsYo (silent background save, no toast)
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.saveLyricsFile === "function") {
      try {
        await MediaSaver.saveLyricsFile({
          lyricsContent: lyrics,
          trackTitle: track.displayTitle,
          trackArtist: track.displayArtist,
          filePath: track.filePath
        });
      } catch (e) {
        console.warn("[LYRICS] Save to LyricsYo directory error:", e);
      }
    }
  }

  async fetchLyrics(track) {
    if (!track) return;
    const trackKey = `nimiyo_lrc_${track.id || track.filePath || (track.displayTitle + "_" + track.displayArtist)}`;
    this.currentLyrics = "";
    this.parsedLrc = [];
    this.activeLyricIndex = -1;
    this.activeIntegratedLyricIndex = -1;
    this.isUserScrollingLyrics = false;
    if (this.elements.fullPlayerLyricsScroll) {
      this.elements.fullPlayerLyricsScroll.scrollTop = 0;
    }
    if (this.elements.lyricsContent) {
      this.elements.lyricsContent.scrollTop = 0;
    }

    // 1. Check Native MediaSaver & Nimiyo/LyricsYo directory first
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.getAudioLyrics === "function") {
      try {
        const res = await MediaSaver.getAudioLyrics({
          filePath: track.filePath,
          trackTitle: track.displayTitle,
          trackArtist: track.displayArtist
        });
        if (res && res.hasLyrics && res.lyrics) {
          this.currentLyrics = res.lyrics.trim();
          this.parseLrcString(this.currentLyrics);
          if (this.parsedLrc.length > 0) {
            this.saveLyricsToCache(trackKey, this.currentLyrics);
            this.updateLyricsBadgesAndRender();
            return;
          }
        }
      } catch (_) {}
    }

    // 2. Check LocalStorage Cache
    try {
      const cached = localStorage.getItem(trackKey);
      if (cached && cached.trim()) {
        this.currentLyrics = cached.trim();
        this.parseLrcString(this.currentLyrics);
        // If cached lyrics have real timestamps, use them!
        if (this.parsedLrc.length > 0 && /\[\d{1,2}:\d{2}/.test(cached)) {
          this.updateLyricsBadgesAndRender();
          return;
        } else {
          this.updateLyricsBadgesAndRender();
        }
      }
    } catch (_) {}

    // 3. Online Fetch from LRCLIB & Fallback
    this.isFetchingLyrics = true;
    if (this.isLyricsModeActive) {
      this.renderIntegratedLyrics();
    }

    try {
      const onlineLyrics = await this.fetchOnlineLyrics(track);
      if (onlineLyrics && this.currentTrack && (this.currentTrack.id === track.id || this.currentTrack.filePath === track.filePath)) {
        const lyrStr = typeof onlineLyrics === "string" ? onlineLyrics : onlineLyrics.lyrics;
        if (lyrStr) {
          this.currentLyrics = lyrStr.trim();
          this.parseLrcString(this.currentLyrics);
          // Automatically save newly detected lyrics into Nimiyo/LyricsYo directory silently (no popup)!
          await this.saveLyricsToStorage(track, this.currentLyrics, false);
        }
      }
    } catch (err) {
      console.warn("[LYRICS] Fetch online error:", err);
    } finally {
      this.isFetchingLyrics = false;
      this.updateLyricsBadgesAndRender();
    }
  }

  openEditLyricsModal() {
    if (!this.currentTrack) {
      if (window.showToast) window.showToast("Pilih lagu terlebih dahulu", "info");
      return;
    }
    if (this.elements.editLyricsTrackTitle) {
      this.elements.editLyricsTrackTitle.innerText = `${this.currentTrack.displayTitle || "Track"} • ${this.currentTrack.displayArtist || "Artist"}`;
    }

    let content = this.currentLyrics || "";
    if (!content && this.parsedLrc && this.parsedLrc.length > 0) {
      content = this.parsedLrc.map(item => {
        const mins = Math.floor(item.time / 60);
        const secs = item.time % 60;
        const mm = String(mins).padStart(2, "0");
        const ss = secs.toFixed(2).padStart(5, "0");
        return `[${mm}:${ss}] ${item.text || ""}`;
      }).join("\n");
    }

    if (this.elements.editLyricsTextarea) {
      this.elements.editLyricsTextarea.value = content;
    }

    if (this.elements.editLyricsModal) {
      this.elements.editLyricsModal.classList.remove("hidden");
    }
  }

  closeEditLyricsModal() {
    if (this.elements.editLyricsModal) {
      this.elements.editLyricsModal.classList.add("hidden");
    }
  }

  openResetLyricsModal() {
    if (!this.currentTrack) {
      if (window.showToast) window.showToast("Pilih lagu terlebih dahulu", "info");
      return;
    }
    if (this.elements.resetLyricsTrackTitle) {
      this.elements.resetLyricsTrackTitle.innerText = `${this.currentTrack.displayTitle || "Track"} • ${this.currentTrack.displayArtist || "Artist"}`;
    }
    if (this.elements.resetLyricsModal) {
      this.elements.resetLyricsModal.classList.remove("hidden");
    }
  }

  closeResetLyricsModal() {
    if (this.elements.resetLyricsModal) {
      this.elements.resetLyricsModal.classList.add("hidden");
    }
  }

  async handleResetLyricsDefault() {
    if (!this.currentTrack) return;
    const track = this.currentTrack;
    const trackKey = `nimiyo_lrc_${track.id || track.filePath || (track.displayTitle + "_" + track.displayArtist)}`;

    // Saat user tekan refresh: lirik otomatis dihapus lebih dulu
    this.currentLyrics = "";
    this.parsedLrc = [];
    if (this.elements.editLyricsTextarea) {
      this.elements.editLyricsTextarea.value = "";
    }
    try {
      localStorage.removeItem(trackKey);
      localStorage.removeItem(trackKey + "_trans");
    } catch (_) {}
    this.updateLyricsBadgesAndRender();

    if (window.showToast) {
      window.showToast("Mencari lirik otomatis (LRCLIB)...", "info");
    }

    try {
      const onlineLyrics = await this.fetchOnlineLyrics(track);
      const lyrStr = typeof onlineLyrics === "string" ? onlineLyrics : (onlineLyrics?.lyrics || null);
      if (lyrStr && lyrStr.trim()) {
        this.currentLyrics = lyrStr.trim();
        this.parseLrcString(this.currentLyrics);
        if (this.elements.editLyricsTextarea) {
          this.elements.editLyricsTextarea.value = this.currentLyrics;
        }
        this.saveLyricsToCache(trackKey, this.currentLyrics);
        await this.saveLyricsToStorage(track, this.currentLyrics, false);
        this.updateLyricsBadgesAndRender();
        this.syncIntegratedLyrics(this.audio.currentTime || 0, true);
        this.closeResetLyricsModal();
        if (window.showToast) {
          window.showToast("Lirik berhasil di-reset dari database online!", "success");
        }
      } else {
        this.closeResetLyricsModal();
        if (window.showToast) {
          window.showToast("Lirik tidak ditemukan di database online", "info");
        }
      }
    } catch (err) {
      this.closeResetLyricsModal();
      if (window.showToast) {
        window.showToast("Gagal mengambil lirik online", "error");
      }
    }
  }

  handleResetLyricsLyricsify() {
    if (!this.currentTrack) return;
    const track = this.currentTrack;
    const trackKey = `nimiyo_lrc_${track.id || track.filePath || (track.displayTitle + "_" + track.displayArtist)}`;

    // Saat user tekan refresh: lirik otomatis dihapus
    this.currentLyrics = "";
    this.parsedLrc = [];
    if (this.elements.editLyricsTextarea) {
      this.elements.editLyricsTextarea.value = "";
    }
    try {
      localStorage.removeItem(trackKey);
      localStorage.removeItem(trackKey + "_trans");
    } catch (_) {}
    this.updateLyricsBadgesAndRender();

    const { title, primaryArtist } = this.cleanTitleAndArtist(track.displayTitle || track.title, track.displayArtist || track.artist);
    const query = primaryArtist ? `${primaryArtist} ${title}` : title;
    const searchUrl = `https://lyricsify.com/search?q=${encodeURIComponent(query.trim())}`;

    this.closeResetLyricsModal();
    window.open(searchUrl, "_blank");

    if (window.showToast) {
      window.showToast("Lirik di-reset. Salin lirik .LRC dari Lyricsify lalu tempel di sini", "info");
    }
  }

  handleImportLyricsFile(e) {
    const file = e.target?.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result;
      if (typeof content === "string") {
        if (this.elements.editLyricsTextarea) {
          this.elements.editLyricsTextarea.value = content.trim();
        }
        this.closeResetLyricsModal();
        if (window.showToast) {
          window.showToast(`Lirik ${file.name} berhasil di-import`, "success");
        }
      }
    };
    reader.onerror = () => {
      if (window.showToast) {
        window.showToast("Gagal membaca file lirik", "error");
      }
    };
    reader.readAsText(file, "UTF-8");
    e.target.value = "";
  }

  async saveEditedLyrics() {
    if (!this.currentTrack) return;
    const textarea = this.elements.editLyricsTextarea;
    const content = textarea ? textarea.value.trim() : "";

    if (!content) {
      if (window.showToast) window.showToast("Lirik tidak boleh kosong", "warning");
      return;
    }

    this.currentLyrics = content;
    this.parseLrcString(this.currentLyrics);

    // Automatically save .lrc to directory nimiyo/LyricsYo and keep it permanently
    await this.saveLyricsToStorage(this.currentTrack, this.currentLyrics, false);

    // Update UI immediately
    this.updateLyricsBadgesAndRender();
    this.syncIntegratedLyrics(this.audio.currentTime || 0, true);

    this.closeEditLyricsModal();

    if (window.showToast) {
      window.showToast("Edit Lirik berhasil, dan disimpan Lokal", "success");
    }
  }

  async downloadLyricsFile() {
    if (!this.currentTrack) return;
    const textarea = this.elements.editLyricsTextarea;
    const content = (textarea && textarea.value.trim()) ? textarea.value.trim() : (this.currentLyrics || "");

    if (!content) {
      if (window.showToast) window.showToast("Belum ada lirik untuk di-download", "warning");
      return;
    }

    const cleanTitle = (this.currentTrack.displayTitle || "lyrics").replace(/[\\/:*?"<>|]/g, "_").trim();
    const cleanArtist = (this.currentTrack.displayArtist || "").replace(/[\\/:*?"<>|]/g, "_").trim();
    const fileName = cleanArtist && !cleanArtist.toLowerCase().includes("unknown") ? `${cleanTitle} - ${cleanArtist}.lrc` : `${cleanTitle}.lrc`;

    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.saveLyricsFile === "function") {
      try {
        const res = await MediaSaver.saveLyricsFile({
          lyricsContent: content,
          fileName: fileName,
          trackTitle: this.currentTrack.displayTitle,
          trackArtist: this.currentTrack.displayArtist,
          filePath: this.currentTrack.filePath
        });
        if (res && res.success) {
          if (window.showToast) {
            window.showToast(`Lirik tersimpan: ${res.fileName || fileName} di Nimiyo/LyricsYo`, "success");
          }
          return;
        }
      } catch (err) {
        console.warn("[LYRICS] Native saveLyricsFile error:", err);
      }
    }

    // Web fallback blob download
    try {
      const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      if (window.showToast) {
        window.showToast(`Lirik ${fileName} berhasil diunduh`, "success");
      }
    } catch (e) {
      if (window.showToast) {
        window.showToast("Gagal mendownload lirik", "error");
      }
    }
  }



  getConsolidatedLrc() {
    if (!this.parsedLrc || this.parsedLrc.length === 0) return [];
    const consolidated = [];

    for (let i = 0; i < this.parsedLrc.length; i++) {
      const cur = this.parsedLrc[i];
      const next = this.parsedLrc[i + 1];

      // Anti double-lyrics: jika ada baris berikutnya yang berada di timestamp sama / berdekatan (< 0.35s),
      // buang baris kedua (terjemahan / baris duplikat lama) dan hanya simpan baris pertama!
      if (next && Math.abs(next.time - cur.time) < 0.35) {
        consolidated.push({
          time: cur.time,
          text: cur.text ? cur.text.trim() : ""
        });
        i++; // skip baris kedua (terjemahan)
      } else {
        consolidated.push({
          time: cur.time,
          text: cur.text ? cur.text.trim() : ""
        });
      }
    }
    return consolidated;
  }

  updateLyricsBadgesAndRender() {
    if (this.elements.fullPlayerLyricsBadge) {
      this.elements.fullPlayerLyricsBadge.classList.toggle("hidden", !this.currentLyrics);
    }
    if (this.isLyricsModeActive) {
      this.renderIntegratedLyrics();
      this.syncIntegratedLyrics(this.audio.currentTime || 0, true);
    }
    if (this.elements.lyricsModal && !this.elements.lyricsModal.classList.contains("hidden")) {
      this.renderLyricsModal();
    }
  }

  toggleLyricsMode(forceState) {
    this.isLyricsModeActive = typeof forceState === "boolean" ? forceState : !this.isLyricsModeActive;
    if (this.isLyricsModeActive) {
      this.elements.fullPlayerArtStage?.classList.add("hidden");
      this.elements.fullPlayerInfoSection?.classList.add("hidden");
      this.elements.fullPlayerLyricsStage?.classList.remove("hidden");

      if (this.currentTrack) {
        if (this.elements.lyricsMiniTitle) this.elements.lyricsMiniTitle.innerText = this.currentTrack.displayTitle;
        if (this.elements.lyricsMiniArtist) this.elements.lyricsMiniArtist.innerText = this.currentTrack.displayArtist;
      }

      this.renderIntegratedLyrics();
      this.syncIntegratedLyrics(this.audio.currentTime || 0, true);
    } else {
      this.elements.fullPlayerLyricsStage?.classList.add("hidden");
      this.elements.fullPlayerArtStage?.classList.remove("hidden");
      this.elements.fullPlayerInfoSection?.classList.remove("hidden");
    }
  }

  renderIntegratedLyrics() {
    if (!this.elements.fullPlayerLyricsScroll) return;

    const consolidated = this.getConsolidatedLrc();
    if (consolidated.length > 0) {
      // Synchronized LRC lines with full stage presentation & tap-to-seek
      const html = consolidated.map((item, idx) => {
        return `
          <div class="flowing-lyric-line" id="flowing-lyric-${idx}" data-line-idx="${idx}" data-time="${item.time}">
            <div class="flowing-lyric-primary">${this.escapeHtml(item.text || "•••")}</div>
          </div>
        `;
      }).join("");
      this.elements.fullPlayerLyricsScroll.innerHTML = html;

      // Setup line tap seek listeners
      this.elements.fullPlayerLyricsScroll.querySelectorAll(".flowing-lyric-line").forEach(el => {
        el.addEventListener("click", (e) => {
          e.stopPropagation();
          const t = parseFloat(el.getAttribute("data-time"));
          if (!isNaN(t)) {
            this.seek(t);
            this.syncIntegratedLyrics(t, true);
          }
        });
      });

      // Bind interactive scroll/drag seeking
      if (this.elements.fullPlayerLyricsScroll) {
        this.elements.fullPlayerLyricsScroll.scrollTop = 0;
      }
      this.setupFlowingLyricsScrollListener();
      this.syncIntegratedLyrics(this.audio.currentTime || 0, true);
    } else if (this.isFetchingLyrics) {
      // Loading state
      this.elements.fullPlayerLyricsScroll.innerHTML = `
        <div class="lyrics-empty-state-box">
          <div class="lyrics-fetching-spinner"></div>
          <span class="lyrics-empty-state-title">${this.t("musicFetchingLyrics", "Mencari lirik lagu online...")}</span>
          <span class="lyrics-empty-state-sub">${this.escapeHtml(this.currentTrack?.displayTitle || "")}</span>
        </div>
      `;
    } else if (this.currentLyrics) {
      // Plain text lyrics
      const lines = this.currentLyrics.split("\n").map(l => {
        const clean = l.replace(/\[\d{2}:\d{2}(?:\.\d{2,3})?\]/g, "").trim();
        return clean ? `<div class="flowing-lyric-line" style="opacity: 0.9;">${this.escapeHtml(clean)}</div>` : `<div style="height:12px;"></div>`;
      }).join("");
      this.elements.fullPlayerLyricsScroll.innerHTML = lines;
    } else {
      // No lyrics found state
      this.elements.fullPlayerLyricsScroll.innerHTML = `
        <div class="lyrics-empty-state-box">
          <span class="lyrics-empty-state-icon">🎤</span>
          <span class="lyrics-empty-state-title">${this.t("musicNoLyrics", "Lirik tidak ditemukan untuk lagu ini")}</span>
          <span class="lyrics-empty-state-sub">${this.t("musicTapToFlip", "Ketuk untuk kembali ke cover")}</span>
        </div>
      `;
    }
  }

  setupFlowingLyricsScrollListener() {
    const scrollEl = this.elements.fullPlayerLyricsScroll;
    const seekPill = this.elements.lyricsSeekPill;
    const seekTimeEl = this.elements.lyricsSeekTime;
    if (!scrollEl) return;

    let isUserDragging = false;
    let targetTime = null;
    let rafId = null;

    const onTouchStart = () => {
      isUserDragging = true;
      this.isUserScrollingLyrics = true;
      clearTimeout(this.lyricsScrollCooldownTimer);
    };

    const updateCenterLineHighlight = () => {
      if (!isUserDragging || !this.parsedLrc || this.parsedLrc.length === 0) return;

      const scrollRect = scrollEl.getBoundingClientRect();
      const centerY = scrollRect.top + scrollRect.height / 2;

      let closestIdx = -1;
      let minDistance = Infinity;

      const lines = scrollEl.querySelectorAll(".flowing-lyric-line");
      for (let i = 0; i < lines.length; i++) {
        const el = lines[i];
        const rect = el.getBoundingClientRect();
        const lineMid = rect.top + rect.height / 2;
        const dist = Math.abs(lineMid - centerY);
        if (dist < minDistance) {
          minDistance = dist;
          closestIdx = i;
        }
      }

      const consolidated = this.getConsolidatedLrc();
      if (closestIdx >= 0 && consolidated[closestIdx]) {
        targetTime = consolidated[closestIdx].time;

        for (let i = 0; i < lines.length; i++) {
          lines[i].classList.toggle("seeking-highlight", i === closestIdx);
        }

        if (seekPill && seekTimeEl) {
          seekTimeEl.innerText = this.formatTime(targetTime);
          seekPill.classList.remove("hidden");
        }
      }
    };

    const onTouchMove = () => {
      if (!isUserDragging) return;
      if (rafId) cancelAnimationFrame(rafId);
      rafId = requestAnimationFrame(updateCenterLineHighlight);
    };

    const onTouchEnd = () => {
      if (!isUserDragging) return;
      isUserDragging = false;

      // When touch is released after dragging, jump to the targeted line
      if (targetTime !== null && !isNaN(targetTime)) {
        this.seek(targetTime);
        this.syncIntegratedLyrics(targetTime, true);
      }

      if (seekPill) seekPill.classList.add("hidden");

      scrollEl.querySelectorAll(".flowing-lyric-line").forEach(el => {
        el.classList.remove("seeking-highlight");
      });

      targetTime = null;

      // Allow 1 second cooldown before resuming automated scroll tracking
      clearTimeout(this.lyricsScrollCooldownTimer);
      this.lyricsScrollCooldownTimer = setTimeout(() => {
        this.isUserScrollingLyrics = false;
        this.syncIntegratedLyrics(this.audio.currentTime || 0, false);
      }, 1000);
    };

    scrollEl.addEventListener("touchstart", onTouchStart, { passive: true });
    scrollEl.addEventListener("touchmove", onTouchMove, { passive: true });
    scrollEl.addEventListener("touchend", onTouchEnd, { passive: true });
    scrollEl.addEventListener("touchcancel", onTouchEnd, { passive: true });

    // Desktop mouse dragging support
    scrollEl.addEventListener("mousedown", onTouchStart);
    window.addEventListener("mousemove", () => { if (isUserDragging) onTouchMove(); });
    window.addEventListener("mouseup", () => { if (isUserDragging) onTouchEnd(); });

    // Clickable seek pill
    if (seekPill) {
      seekPill.onclick = (e) => {
        e.stopPropagation();
        if (targetTime !== null) {
          this.seek(targetTime);
          onTouchEnd();
        }
      };
    }
  }

  generateEstimatedLrcFromPlainText(plainText, trackDuration) {
    if (!plainText) return [];
    const rawLines = plainText.split("\n").map(l => l.trim()).filter(Boolean);
    if (rawLines.length === 0) return [];

    const duration = trackDuration > 20 ? trackDuration : (this.audio.duration || 180);
    const startTime = Math.min(5, duration * 0.05);
    const endTime = Math.max(startTime + 10, duration * 0.95);
    const timeSpan = endTime - startTime;
    const interval = timeSpan / rawLines.length;

    return rawLines.map((line, idx) => ({
      time: startTime + (idx * interval),
      text: line
    }));
  }

  syncIntegratedLyrics(currentTime, forceScroll = false) {
    const consolidated = this.getConsolidatedLrc();
    if (consolidated.length === 0 || !this.isLyricsModeActive || !this.elements.fullPlayerLyricsScroll) {
      return;
    }

    let activeIdx = -1;
    for (let i = 0; i < consolidated.length; i++) {
      if (consolidated[i].time <= currentTime) {
        activeIdx = i;
      } else {
        break;
      }
    }

    if (activeIdx !== this.activeIntegratedLyricIndex || forceScroll) {
      this.activeIntegratedLyricIndex = activeIdx;
      const scrollEl = this.elements.fullPlayerLyricsScroll;
      const lines = scrollEl.querySelectorAll(".flowing-lyric-line");

      for (let i = 0; i < lines.length; i++) {
        lines[i].classList.toggle("active", i === activeIdx);
      }

      // Smooth auto-scroll to center active line or top if not started yet
      if (activeIdx >= 0 && (!this.isUserScrollingLyrics || forceScroll)) {
        const activeEl = document.getElementById(`flowing-lyric-${activeIdx}`);
        if (activeEl) {
          const containerHeight = scrollEl.clientHeight;
          const elTop = activeEl.offsetTop;
          const elHeight = activeEl.offsetHeight;
          const targetTop = elTop - (containerHeight / 2) + (elHeight / 2);
          scrollEl.scrollTo({
            top: Math.max(0, targetTop),
            behavior: forceScroll ? "auto" : "smooth"
          });
        }
      } else if (activeIdx < 0 && (!this.isUserScrollingLyrics || forceScroll)) {
        // Even if lyric line hasn't started yet (intro/music), scroll to top immediately!
        scrollEl.scrollTo({
          top: 0,
          behavior: forceScroll ? "auto" : "smooth"
        });
      }
    }
  }

  parseLrcString(raw) {
    this.parsedLrc = [];
    if (!raw) return;

    const timeRegex = /\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
    const lines = raw.split("\n");

    for (const line of lines) {
      if (/^\[(ti|ar|al|by|offset|length|re|ve):/i.test(line.trim())) continue;
      const matches = [...line.matchAll(timeRegex)];
      if (matches.length > 0) {
        let cleanText = line.replace(timeRegex, "").replace(/<[^>]+>/g, "").trim();
        for (const match of matches) {
          const mins = parseInt(match[1], 10);
          const secs = parseInt(match[2], 10);
          let ms = 0;
          if (match[3]) {
            const rawMs = match[3];
            if (rawMs.length === 1) ms = parseInt(rawMs, 10) * 100;
            else if (rawMs.length === 2) ms = parseInt(rawMs, 10) * 10;
            else ms = parseInt(rawMs.substring(0, 3), 10);
          }
          const totalSec = mins * 60 + secs + (ms / 1000);
          this.parsedLrc.push({ time: totalSec, text: cleanText });
        }
      }
    }

    // Sort chronologically by timestamp
    this.parsedLrc.sort((a, b) => a.time - b.time);

    // If no timestamps found but plain text exists, create estimated timestamps so lyrics always advance!
    if (this.parsedLrc.length === 0 && raw.trim().length > 0) {
      const dur = this.currentTrack?.duration ? (this.currentTrack.duration > 1000 ? this.currentTrack.duration / 1000 : this.currentTrack.duration) : (this.audio.duration || 0);
      this.parsedLrc = this.generateEstimatedLrcFromPlainText(raw, dur);
    }
  }

  renderLyricsModal() {
    if (!this.currentTrack) return;
    if (this.elements.lyricsModalTitle) this.elements.lyricsModalTitle.innerText = this.currentTrack.displayTitle;
    if (this.elements.lyricsModalArtist) this.elements.lyricsModalArtist.innerText = this.currentTrack.displayArtist;

    if (this.elements.lyricsContent) {
      const consolidated = this.getConsolidatedLrc();
      if (consolidated.length > 0) {
        // Synchronized LRC with tap-to-seek
        const html = consolidated.map((item, idx) => {
          return `
            <div class="lyrics-line synced-line" id="lyric-line-${idx}" data-lyric-idx="${idx}" data-time="${item.time}">
              <div class="lyrics-line-primary">${this.escapeHtml(item.text || "•••")}</div>
            </div>
          `;
        }).join("");
        this.elements.lyricsContent.innerHTML = html;

        // Add tap-to-seek click listener to every line
        this.elements.lyricsContent.querySelectorAll(".synced-line").forEach(line => {
          line.addEventListener("click", () => {
            const time = parseFloat(line.getAttribute("data-time"));
            if (!isNaN(time)) {
              this.seek(time);
            }
          });
        });

        this.syncActiveLyric(this.audio.currentTime || 0, true);
      } else if (this.currentLyrics) {
        // Plain text lyrics
        const lines = this.currentLyrics.split("\n").map(l => {
          const clean = l.replace(/\[\d{2}:\d{2}(?:\.\d{2,3})?\]/g, "").trim();
          return clean ? `<p class="lyrics-line">${this.escapeHtml(clean)}</p>` : `<div class="lyrics-space"></div>`;
        }).join("");
        this.elements.lyricsContent.innerHTML = lines;
      } else {
        this.elements.lyricsContent.innerHTML = `
          <div class="lyrics-empty-state">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M9 18V5l12-2v13"></path>
              <circle cx="6" cy="18" r="3"></circle>
              <circle cx="18" cy="16" r="3"></circle>
              <line x1="3" y1="3" x2="21" y2="21" stroke-width="2"></line>
            </svg>
            <p>${this.t("musicNoLyrics", "Tidak ada lirik lokal untuk lagu ini")}</p>
          </div>
        `;
      }
    }
  }

  syncActiveLyric(currentTime, forceScroll = false) {
    const consolidated = this.getConsolidatedLrc();
    if (consolidated.length === 0 || !this.elements.lyricsModal || this.elements.lyricsModal.classList.contains("hidden")) {
      return;
    }

    let activeIdx = -1;
    for (let i = 0; i < consolidated.length; i++) {
      if (consolidated[i].time <= currentTime) {
        activeIdx = i;
      } else {
        break;
      }
    }

    if (activeIdx !== this.activeLyricIndex || forceScroll) {
      this.activeLyricIndex = activeIdx;
      document.querySelectorAll(".synced-line").forEach((el, idx) => {
        el.classList.toggle("active-lyric", idx === activeIdx);
      });

      if (activeIdx >= 0) {
        const activeEl = document.getElementById(`lyric-line-${activeIdx}`);
        if (activeEl) {
          activeEl.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      } else {
        if (this.elements.lyricsContent) {
          this.elements.lyricsContent.scrollTop = 0;
        }
      }
    }
  }

  // -------------------------------------------------------------
  // Visualizer Animation
  // -------------------------------------------------------------
  startVisualizer() {
    this.stopVisualizer();
    this.elements.miniPlayerVisualizer?.classList.add("playing");
    this.elements.fullPlayerVisualizer?.classList.add("playing");

    const bars = document.querySelectorAll(".visualizer-bar");
    if (bars.length > 0) {
      this.visualizerInterval = setInterval(() => {
        if (!this.isPlaying) return;
        bars.forEach(bar => {
          const scale = ((Math.floor(Math.random() * 85) + 15) / 100).toFixed(2);
          bar.style.transform = `scaleY(${scale})`;
        });
      }, 120);
    }
  }

  stopVisualizer() {
    if (this.visualizerInterval) {
      clearInterval(this.visualizerInterval);
      this.visualizerInterval = null;
    }
    this.elements.miniPlayerVisualizer?.classList.remove("playing");
    this.elements.fullPlayerVisualizer?.classList.remove("playing");
    const bars = document.querySelectorAll(".visualizer-bar");
    bars.forEach(bar => bar.style.transform = "scaleY(0.25)");
  }

  // -------------------------------------------------------------
  // UI Renderers & Tabs
  // -------------------------------------------------------------
  switchSubTab(tabName) {
    if (this.isSelectionMode) {
      this.exitSelectionMode();
    }
    this.activeTab = tabName;
    this.selectedArtist = null;
    this.selectedAlbum = null;
    this.elements.playerNavContainer?.classList.remove("hidden");
    this.elements.playerSearchBar?.classList.remove("hidden");

    document.querySelectorAll(".player-tab-btn").forEach(btn => {
      btn.classList.toggle("active", btn.getAttribute("data-subtab") === tabName);
    });

    this.updateSubTabIndicator();
    this.renderCurrentTab();
  }

  updateSubTabIndicator() {
    const activeBtn = document.querySelector(`.player-tab-btn[data-subtab="${this.activeTab}"]`);
    const indicator = this.elements.playerTabIndicator;
    const bar = this.elements.playerTabBar;
    if (!indicator || !bar || !activeBtn) return;

    const barRect = bar.getBoundingClientRect();
    const btnRect = activeBtn.getBoundingClientRect();
    if (btnRect.width === 0 || barRect.width === 0) return;

    const barBorderLeft = parseFloat(getComputedStyle(bar).borderLeftWidth) || 0;
    const targetX = Math.round(btnRect.left - barRect.left - barBorderLeft);
    const targetWidth = Math.round(btnRect.width);

    indicator.style.width = `${targetWidth}px`;
    indicator.style.transform = `translateX(${targetX}px)`;
  }

  renderCurrentTab() {
    if (this.selectedArtist) {
      this.renderArtistDrilldown(this.selectedArtist);
      return;
    }

    if (this.selectedAlbum) {
      this.renderAlbumDrilldown(this.selectedAlbum);
      return;
    }

    // Restore top navigation & search bar when in standard tab view
    this.elements.playerNavContainer?.classList.remove("hidden");
    this.elements.playerSearchBar?.classList.remove("hidden");
    if (this.elements.backSubHeaderBtn) this.elements.backSubHeaderBtn.classList.add("hidden");
    if (this.elements.subHeaderTitle) this.elements.subHeaderTitle.innerText = "PLAYER";

    [
      this.elements.paneNimiyo,
      this.elements.paneSongs,
      this.elements.paneArtists,
      this.elements.paneAlbums,
      this.elements.paneDrilldown
    ].forEach(p => p && p.classList.add("hidden"));

    const q = (this.elements.searchInput?.value || "").trim().toLowerCase();

    switch (this.activeTab) {
      case "nimiyo":
        this.elements.paneNimiyo?.classList.remove("hidden");
        this.renderTrackList(this.elements.paneNimiyo, this.sortTracks(this.filterTracks(this.nimiyoTracks, q)), "nimiyo");
        break;
      case "songs":
        this.elements.paneSongs?.classList.remove("hidden");
        this.renderTrackList(this.elements.paneSongs, this.sortTracks(this.filterTracks(this.allTracks, q)), "songs");
        break;
      case "artists":
        this.elements.paneArtists?.classList.remove("hidden");
        this.renderArtistsGrid(this.elements.paneArtists, q);
        break;
      case "album":
        this.elements.paneAlbums?.classList.remove("hidden");
        this.renderAlbumsGrid(this.elements.paneAlbums, q);
        break;
    }
  }

  filterTracks(tracks, query) {
    if (!query) return tracks;
    return tracks.filter(t =>
      (t.displayTitle && t.displayTitle.toLowerCase().includes(query)) ||
      (t.displayArtist && t.displayArtist.toLowerCase().includes(query)) ||
      (t.displayAlbum && t.displayAlbum.toLowerCase().includes(query))
    );
  }

  renderTrackList(container, tracks, sourceTab) {
    if (!container) return;

    if (!tracks || tracks.length === 0) {
      const emptyMsg = sourceTab === "nimiyo"
        ? this.t("musicNoNimiyoSongs", "Belum ada audio di folder musik NIMIYO")
        : this.t("musicNoSongs", "Tidak ada file audio lokal ditemukan");
      container.innerHTML = `
        <div class="music-empty-state">
          <div class="empty-icon">🎵</div>
          <p>${emptyMsg}</p>
        </div>
      `;
      return;
    }

    const listPlaceholder = "nimiyo_icon.webp";
    const html = tracks.map((track, idx) => {
      const isCurrent = this.currentTrack && this.currentTrack.id === track.id;
      const isCurrentPlaying = isCurrent && this.isPlaying;
      const isSelected = this.isSelectionMode && this.selectedTrackIds.has(track.id);
      const durStr = track.duration > 0 ? this.formatTime(track.duration / 1000) : "--:--";
      const lyricsBadge = track.hasLyrics ? `<span class="badge-lyrics" title="Lyrics available">LRC</span>` : "";
      const initialThumb = (this.artworkCache.get(track.id) || track.artwork || track.thumbnail || listPlaceholder);
      const overlaySvg = isCurrentPlaying
        ? `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>`
        : `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>`;

      return `
        <div class="song-card ${isCurrent ? 'now-playing' : ''} ${isCurrentPlaying ? 'is-playing' : ''} ${isSelected ? 'selected' : ''}" data-track-id="${track.id}" data-idx="${idx}">
          <div class="song-select-checkbox">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="3">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </div>
          <div class="song-art-wrapper">
            <img class="song-art-thumb lazy-art" data-track-id="${track.id}" src="${initialThumb}" onerror="this.onerror=null;this.src='${listPlaceholder}';" alt="Art">
            <div class="song-play-overlay">
              ${overlaySvg}
            </div>
          </div>
          <div class="song-info">
            <div class="song-title-row">
              <span class="song-title">${this.escapeHtml(track.displayTitle)}</span>
              ${lyricsBadge}
            </div>
            <div class="song-sub-row">
              <span class="song-artist">${this.escapeHtml(track.displayArtist)}</span>
              <span class="song-duration">${durStr}</span>
            </div>
          </div>
          <div class="song-actions">
            <button class="icon-btn song-more-btn" data-track-id="${track.id}" title="Options">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5">
                <circle cx="12" cy="12" r="1.5"></circle>
                <circle cx="12" cy="5" r="1.5"></circle>
                <circle cx="12" cy="19" r="1.5"></circle>
              </svg>
            </button>
          </div>
        </div>
      `;
    }).join("");

    container.innerHTML = `<div class="songs-list-view">${html}</div>`;

    this.lazyLoadVisibleArtworks(container, tracks);

    container.querySelectorAll(".song-card").forEach(card => {
      const trackId = card.getAttribute("data-track-id");
      const track = tracks.find(t => t.id === trackId);
      if (track) {
        this.bindTrackSelectionGestures(card, track, tracks);
        card.addEventListener("click", (e) => {
          if (e.target.closest(".song-more-btn") || e.target.closest(".song-select-checkbox")) return;
          if (this.isSelectionMode) return;
          this.playTrackFromList(track, tracks);
        });
      }
    });

    container.querySelectorAll(".song-more-btn").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const trackId = btn.getAttribute("data-track-id");
        const track = tracks.find(t => t.id === trackId);
        if (track) {
          this.showTrackContextMenu(track);
        }
      });
    });
  }

  renderArtistsGrid(container, query) {
    if (!container) return;
    const artists = Array.from(this.artistsMap.values()).filter(a =>
      !query || a.name.toLowerCase().includes(query)
    );

    if (artists.length === 0) {
      container.innerHTML = `
        <div class="music-empty-state">
          <div class="empty-icon">🎤</div>
          <p>${this.t("musicNoArtists", "Tidak ada artist ditemukan")}</p>
        </div>
      `;
      return;
    }

    const artistPlaceholder = "nimiyo_icon.webp";
    const html = artists.map((art, idx) => {
      const count = art.tracks.length;
      const initialThumb = (art.artworkTrack ? (this.artworkCache.get(art.artworkTrack.id) || art.artworkTrack.artwork || art.artworkTrack.thumbnail) : null) || artistPlaceholder;
      return `
        <div class="artist-card" data-idx="${idx}" data-artist-name="${this.escapeHtml(art.name)}">
          <div class="artist-avatar-wrapper">
            <img class="artist-avatar lazy-art" data-track-id="${art.artworkTrack?.id}" src="${initialThumb}" onerror="this.onerror=null;this.src='${artistPlaceholder}';" alt="${this.escapeHtml(art.name)}">
          </div>
          <div class="artist-name">${this.escapeHtml(art.name)}</div>
          <div class="artist-track-count">${count} ${count === 1 ? 'Track' : 'Tracks'}</div>
        </div>
      `;
    }).join("");

    container.innerHTML = `<div class="artists-grid-view">${html}</div>`;
    this.lazyLoadVisibleArtworks(container, artists.map(a => a.artworkTrack).filter(Boolean));

    container.querySelectorAll(".artist-card").forEach((card) => {
      card.addEventListener("click", () => {
        const idx = parseInt(card.getAttribute("data-idx"), 10);
        const artistObj = (!isNaN(idx) && artists[idx]) ? artists[idx] : this.artistsMap.get(card.getAttribute("data-artist-name"));
        if (artistObj) {
          this.renderArtistDrilldown(artistObj);
        }
      });
    });
  }

  renderAlbumsGrid(container, query) {
    if (!container) return;
    const albums = Array.from(this.albumsMap.values()).filter(a =>
      !query || a.title.toLowerCase().includes(query) || a.artist.toLowerCase().includes(query)
    );

    if (albums.length === 0) {
      container.innerHTML = `
        <div class="music-empty-state">
          <div class="empty-icon">💿</div>
          <p>${this.t("musicNoAlbums", "Tidak ada album ditemukan")}</p>
        </div>
      `;
      return;
    }

    const albumPlaceholder = "nimiyo_icon.webp";
    const html = albums.map((alb, idx) => {
      const count = alb.tracks.length;
      const initialThumb = (alb.artworkTrack ? (this.artworkCache.get(alb.artworkTrack.id) || alb.artworkTrack.artwork || alb.artworkTrack.thumbnail) : null) || albumPlaceholder;
      return `
        <div class="album-card" data-idx="${idx}" data-album-key="${this.escapeHtml(alb.title)}___${this.escapeHtml(alb.artist)}">
          <div class="album-cover-wrapper">
            <img class="album-cover lazy-art" data-track-id="${alb.artworkTrack?.id}" src="${initialThumb}" onerror="this.onerror=null;this.src='${albumPlaceholder}';" alt="${this.escapeHtml(alb.title)}">
          </div>
          <div class="album-title">${this.escapeHtml(alb.title)}</div>
          <div class="album-artist">${this.escapeHtml(alb.artist)}</div>
          <div class="album-track-count">${count} ${count === 1 ? 'Track' : 'Tracks'}</div>
        </div>
      `;
    }).join("");

    container.innerHTML = `<div class="albums-grid-view">${html}</div>`;
    this.lazyLoadVisibleArtworks(container, albums.map(a => a.artworkTrack).filter(Boolean));

    container.querySelectorAll(".album-card").forEach((card) => {
      card.addEventListener("click", () => {
        const idx = parseInt(card.getAttribute("data-idx"), 10);
        const albObj = (!isNaN(idx) && albums[idx]) ? albums[idx] : this.albumsMap.get(card.getAttribute("data-album-key"));
        if (albObj) {
          this.renderAlbumDrilldown(albObj);
        }
      });
    });
  }

  renderArtistDrilldown(artistObj) {
    if (!artistObj) return;
    this.selectedArtist = artistObj;
    this.selectedAlbum = null;

    // 1. Hide all standard sub-panes
    [
      this.elements.paneNimiyo,
      this.elements.paneSongs,
      this.elements.paneArtists,
      this.elements.paneAlbums
    ].forEach(p => p && p.classList.add("hidden"));

    // 2. Hide top navigation bar & search bar during drilldown
    this.elements.playerNavContainer?.classList.add("hidden");
    this.elements.playerSearchBar?.classList.add("hidden");

    // 3. Show drilldown pane & update subheader
    this.elements.paneDrilldown?.classList.remove("hidden");
    if (this.elements.backSubHeaderBtn) this.elements.backSubHeaderBtn.classList.remove("hidden");
    if (this.elements.subHeaderTitle) this.elements.subHeaderTitle.innerText = artistObj.name;

    // 4. Scroll body back to top
    if (this.elements.tabPlayer) {
      const playerBody = this.elements.tabPlayer.querySelector(".player-body");
      if (playerBody) playerBody.scrollTop = 0;
    }

    const artistHeroPlaceholder = (artistObj.artworkTrack ? (this.artworkCache.get(artistObj.artworkTrack.id) || artistObj.artworkTrack.artwork || artistObj.artworkTrack.thumbnail) : null) || "nimiyo_icon.webp";
    const totalDurationMs = (artistObj.tracks || []).reduce((acc, t) => acc + (t.duration || 0), 0);
    const totalDurationStr = totalDurationMs > 0 ? this.formatTotalDuration(totalDurationMs) : "";
    const albumsCount = artistObj.albums?.size || 0;
    const trackCount = artistObj.tracks ? artistObj.tracks.length : 0;

    // Render Hero Card with Play All and Shuffle buttons
    const heroHtml = `
      <div class="drilldown-hero-card">
        <div class="drilldown-hero-art-wrapper is-artist">
          <img class="lazy-art" data-track-id="${artistObj.artworkTrack?.id}" src="${artistHeroPlaceholder}" onerror="this.onerror=null;this.src='nimiyo_icon.webp';" alt="Artist">
        </div>
        <div class="drilldown-hero-title">${this.escapeHtml(artistObj.name)}</div>
        <div class="drilldown-hero-subtitle">${trackCount} ${trackCount === 1 ? 'Track' : 'Tracks'} • ${albumsCount} ${albumsCount === 1 ? 'Album' : 'Albums'}${totalDurationStr ? ' • ' + totalDurationStr : ''}</div>
        <div class="drilldown-hero-actions">
          <button id="drilldownPlayAllBtn" class="btn primary-btn drilldown-action-btn">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
            <span>Play All</span>
          </button>
          <button id="drilldownShuffleBtn" class="btn outline-btn drilldown-action-btn">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="16 3 21 3 21 8"></polyline>
              <line x1="4" y1="20" x2="21" y2="3"></line>
              <polyline points="21 16 21 21 16 21"></polyline>
              <line x1="15" y1="15" x2="21" y2="21"></line>
              <line x1="4" y1="4" x2="9" y2="9"></line>
            </svg>
            <span>Shuffle</span>
          </button>
        </div>
      </div>
      <div id="drilldownTracksSubList"></div>
    `;

    if (this.elements.paneDrilldown) {
      this.elements.paneDrilldown.innerHTML = heroHtml;
      const subList = document.getElementById("drilldownTracksSubList");
      this.renderTrackList(subList, artistObj.tracks || [], "artist");

      // Bind Hero Action Buttons
      document.getElementById("drilldownPlayAllBtn")?.addEventListener("click", () => {
        if (artistObj.tracks && artistObj.tracks.length > 0) {
          this.playTrackFromList(artistObj.tracks[0], artistObj.tracks);
        }
      });
      document.getElementById("drilldownShuffleBtn")?.addEventListener("click", () => {
        if (artistObj.tracks && artistObj.tracks.length > 0) {
          this.shuffleAll(artistObj.tracks);
        }
      });

      this.lazyLoadVisibleArtworks(this.elements.paneDrilldown, [artistObj.artworkTrack].filter(Boolean));
    }
  }

  renderAlbumDrilldown(albObj) {
    if (!albObj) return;
    this.selectedAlbum = albObj;
    this.selectedArtist = null;

    // 1. Hide all standard sub-panes
    [
      this.elements.paneNimiyo,
      this.elements.paneSongs,
      this.elements.paneArtists,
      this.elements.paneAlbums
    ].forEach(p => p && p.classList.add("hidden"));

    // 2. Hide top navigation bar & search bar during drilldown
    this.elements.playerNavContainer?.classList.add("hidden");
    this.elements.playerSearchBar?.classList.add("hidden");

    // 3. Show drilldown pane & update subheader
    this.elements.paneDrilldown?.classList.remove("hidden");
    if (this.elements.backSubHeaderBtn) this.elements.backSubHeaderBtn.classList.remove("hidden");
    if (this.elements.subHeaderTitle) this.elements.subHeaderTitle.innerText = albObj.title;

    // 4. Scroll body back to top
    if (this.elements.tabPlayer) {
      const playerBody = this.elements.tabPlayer.querySelector(".player-body");
      if (playerBody) playerBody.scrollTop = 0;
    }

    const albumHeroPlaceholder = (albObj.artworkTrack ? (this.artworkCache.get(albObj.artworkTrack.id) || albObj.artworkTrack.artwork || albObj.artworkTrack.thumbnail) : null) || "nimiyo_icon.webp";
    const totalDurationMs = (albObj.tracks || []).reduce((acc, t) => acc + (t.duration || 0), 0);
    const totalDurationStr = totalDurationMs > 0 ? this.formatTotalDuration(totalDurationMs) : "";
    const trackCount = albObj.tracks ? albObj.tracks.length : 0;

    // Render Hero Card with Play All and Shuffle buttons
    const heroHtml = `
      <div class="drilldown-hero-card">
        <div class="drilldown-hero-art-wrapper">
          <img class="lazy-art" data-track-id="${albObj.artworkTrack?.id}" src="${albumHeroPlaceholder}" onerror="this.onerror=null;this.src='nimiyo_icon.webp';" alt="Album">
        </div>
        <div class="drilldown-hero-title">${this.escapeHtml(albObj.title)}</div>
        <div class="drilldown-hero-subtitle">${this.escapeHtml(albObj.artist)} • ${trackCount} ${trackCount === 1 ? 'Track' : 'Tracks'}${albObj.year ? ' • ' + albObj.year : ''}${totalDurationStr ? ' • ' + totalDurationStr : ''}</div>
        <div class="drilldown-hero-actions">
          <button id="drilldownPlayAllBtn" class="btn primary-btn drilldown-action-btn">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
            <span>Play All</span>
          </button>
          <button id="drilldownShuffleBtn" class="btn outline-btn drilldown-action-btn">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="16 3 21 3 21 8"></polyline>
              <line x1="4" y1="20" x2="21" y2="3"></line>
              <polyline points="21 16 21 21 16 21"></polyline>
              <line x1="15" y1="15" x2="21" y2="21"></line>
              <line x1="4" y1="4" x2="9" y2="9"></line>
            </svg>
            <span>Shuffle</span>
          </button>
        </div>
      </div>
      <div id="drilldownTracksSubList"></div>
    `;

    if (this.elements.paneDrilldown) {
      this.elements.paneDrilldown.innerHTML = heroHtml;
      const subList = document.getElementById("drilldownTracksSubList");
      this.renderTrackList(subList, albObj.tracks || [], "album");

      // Bind Hero Action Buttons
      document.getElementById("drilldownPlayAllBtn")?.addEventListener("click", () => {
        if (albObj.tracks && albObj.tracks.length > 0) {
          this.playTrackFromList(albObj.tracks[0], albObj.tracks);
        }
      });
      document.getElementById("drilldownShuffleBtn")?.addEventListener("click", () => {
        if (albObj.tracks && albObj.tracks.length > 0) {
          this.shuffleAll(albObj.tracks);
        }
      });

      this.lazyLoadVisibleArtworks(this.elements.paneDrilldown, [albObj.artworkTrack].filter(Boolean));
    }
  }

  closeDrilldown() {
    this.selectedArtist = null;
    this.selectedAlbum = null;
    this.elements.playerNavContainer?.classList.remove("hidden");
    this.elements.playerSearchBar?.classList.remove("hidden");
    this.renderCurrentTab();
  }

  lazyLoadVisibleArtworks(container, tracks) {
    if (!container || !tracks) return;
    const lazyImages = container.querySelectorAll(".lazy-art");
    lazyImages.forEach(async (img) => {
      const trackId = img.getAttribute("data-track-id");
      const track = tracks.find(t => t.id === trackId);
      if (track) {
        if (this.artworkCache.has(track.id)) {
          const cached = this.artworkCache.get(track.id);
          if (cached) img.src = cached;
        } else if (track.artwork || track.thumbnail) {
          img.src = track.artwork || track.thumbnail;
        } else if (track.hasArtwork) {
          const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
          if (MediaSaver && typeof MediaSaver.getAudioArtwork === "function") {
            try {
              const res = await MediaSaver.getAudioArtwork({
                filePath: track.filePath || "",
                contentUri: track.contentUri || ""
              });
              if (res && res.hasArtwork && res.artwork) {
                this.artworkCache.set(track.id, res.artwork);
                img.src = res.artwork;
              }
            } catch (_) {}
          }
        }
      }
    });
  }

  // -------------------------------------------------------------
  // UI Controls & States Updates
  // -------------------------------------------------------------
  updateTrackMetadataUi(track) {
    if (!track) return;
    if (this.elements.miniPlayerTitle) this.elements.miniPlayerTitle.innerText = track.displayTitle;
    if (this.elements.miniPlayerArtist) this.elements.miniPlayerArtist.innerText = track.displayArtist;

    if (this.elements.fullPlayerTitle) this.elements.fullPlayerTitle.innerText = track.displayTitle;
    if (this.elements.fullPlayerArtist) this.elements.fullPlayerArtist.innerText = track.displayArtist;
    if (this.elements.fullPlayerAlbum) this.elements.fullPlayerAlbum.innerText = track.displayAlbum;

    if (this.elements.lyricsMiniTitle) this.elements.lyricsMiniTitle.innerText = track.displayTitle;
    if (this.elements.lyricsMiniArtist) this.elements.lyricsMiniArtist.innerText = track.displayArtist;

    this.updateTrackItemIcons(this.isPlaying);
  }

  updateTrackItemIcons(isPlaying) {
    const cardPlayIcon = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>`;
    const cardPauseIcon = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>`;

    document.querySelectorAll(".song-card").forEach(c => {
      const isCurrent = this.currentTrack && c.getAttribute("data-track-id") === this.currentTrack.id;
      c.classList.toggle("now-playing", isCurrent);
      c.classList.toggle("is-playing", isCurrent && isPlaying);
      const overlay = c.querySelector(".song-play-overlay");
      if (overlay) {
        overlay.innerHTML = (isCurrent && isPlaying) ? cardPauseIcon : cardPlayIcon;
      }
    });

    document.querySelectorAll(".playlist-track-item").forEach(item => {
      const isCurrent = this.currentTrack && item.getAttribute("data-track-id") === this.currentTrack.id;
      item.classList.toggle("active-playing", isCurrent);
      item.classList.toggle("is-playing", isCurrent && isPlaying);
      const overlay = item.querySelector(".playlist-play-overlay");
      if (overlay) {
        overlay.innerHTML = (isCurrent && isPlaying) ? cardPauseIcon : cardPlayIcon;
        overlay.classList.toggle("active", isCurrent);
      }
    });

    document.querySelectorAll(".queue-item").forEach(item => {
      const idx = parseInt(item.getAttribute("data-queue-idx"), 10);
      const isCurrent = idx === this.queueIndex;
      item.classList.toggle("active", isCurrent);
      const idxEl = item.querySelector(".queue-idx");
      if (idxEl) {
        idxEl.innerText = isCurrent ? (isPlaying ? '⏸' : '▶') : (idx + 1);
      }
    });
  }

  updatePlaybackUiState(isPlaying) {
    const playIconSvg = `<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>`;
    const pauseIconSvg = `<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>`;

    if (this.elements.miniPlayerPlayBtn) {
      this.elements.miniPlayerPlayBtn.innerHTML = isPlaying ? pauseIconSvg : playIconSvg;
    }
    if (this.elements.miniPlayerCompactPlayBtn) {
      this.elements.miniPlayerCompactPlayBtn.innerHTML = isPlaying
        ? `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>`
        : `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>`;
    }
    if (this.elements.fullPlayerPlayBtn) {
      this.elements.fullPlayerPlayBtn.innerHTML = isPlaying ? pauseIconSvg : playIconSvg;
    }

    this.updateTrackItemIcons(isPlaying);
  }

  updateProgressUi(cur, dur) {
    const curSec = Math.floor(cur);
    const durSec = Math.floor(dur);
    const pct = durSec > 0 ? (curSec / durSec) * 100 : 0;

    if (this.elements.miniPlayerProgress) {
      this.elements.miniPlayerProgress.style.width = `${pct}%`;
    }
    if (this.elements.fullPlayerProgressBar) {
      this.elements.fullPlayerProgressBar.value = curSec;
      this.elements.fullPlayerProgressBar.max = durSec || 100;
      this.elements.fullPlayerProgressBar.style.setProperty("--progress", `${pct.toFixed(2)}%`);
    }
    if (this.elements.fullPlayerCurrentTime) {
      this.elements.fullPlayerCurrentTime.innerText = this.formatTime(curSec);
    }
    if (this.elements.fullPlayerDuration) {
      if (this.durationDisplayMode === "remaining" && durSec > 0) {
        const rem = Math.max(0, durSec - curSec);
        this.elements.fullPlayerDuration.innerText = `-${this.formatTime(rem)}`;
      } else {
        this.elements.fullPlayerDuration.innerText = durSec > 0 ? this.formatTime(durSec) : "--:--";
      }
    }
  }

  updateControlStatesUi() {
    if (this.elements.fullPlayerShuffleBtn) {
      this.elements.fullPlayerShuffleBtn.classList.toggle("active", this.isShuffle);
    }

    if (this.elements.fullPlayerRepeatBtn) {
      this.elements.fullPlayerRepeatBtn.classList.toggle("active", this.repeatMode !== "off");
      this.elements.fullPlayerRepeatBtn.setAttribute("data-repeat-mode", this.repeatMode);
      const badge = this.elements.fullPlayerRepeatBtn.querySelector(".repeat-badge");
      if (badge) {
        badge.innerText = this.repeatMode === "one" ? "1" : "";
        badge.classList.toggle("hidden", this.repeatMode !== "one");
      }
    }
  }

  updateLibraryStatsUi() {
    if (this.elements.libraryStats) {
      const nimiyoCount = this.nimiyoTracks.length;
      const totalCount = this.allTracks.length;
      const songsWord = this.t("playlistSongsCount", "Lagu");
      this.elements.libraryStats.innerText = `${nimiyoCount} NIMIYO • ${totalCount} ${songsWord}`;
    }
  }

  updateMediaSessionMetadata(track) {
    if ("mediaSession" in navigator && track) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.displayTitle,
        artist: track.displayArtist,
        album: track.displayAlbum,
        artwork: [
          { src: this.getPlaceholderArtworkSvg(), sizes: "96x96", type: "image/svg+xml" },
          { src: this.getPlaceholderArtworkSvg(), sizes: "512x512", type: "image/svg+xml" }
        ]
      });
    }
  }

  updateMediaSessionState(state) {
    if ("mediaSession" in navigator) {
      navigator.mediaSession.playbackState = state;
    }
  }

  async updateNativeNotification(isPlaying, overridePosition = null) {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.showMusicPlaybackNotification === "function") {
      try {
        const title = this.currentTrack ? (this.currentTrack.displayTitle || this.currentTrack.title || "NIMIYO Player") : "NIMIYO Player";
        const artist = this.currentTrack ? (this.currentTrack.displayArtist || this.currentTrack.artist || "") : "";
        const album = this.currentTrack ? (this.currentTrack.displayAlbum || this.currentTrack.album || "") : "";
        let artwork = this.currentTrack ? (this.artworkCache.get(this.currentTrack.id) || this.currentTrack.artwork || this.currentTrack.thumbnail || null) : null;
        if (artwork && (artwork.includes("image/svg") || artwork.startsWith("<svg"))) {
          artwork = null;
        }
        const duration = this.currentTrack?.duration || Math.round((this.audio.duration || 0) * 1000);
        const position = (overridePosition !== null)
          ? Math.round(overridePosition * 1000)
          : Math.round((this.audio.currentTime || 0) * 1000);

        const playbackSpeed = (this.bpmRate && typeof this.bpmRate === "number")
          ? (this.bpmRate / 100)
          : 1.0;

        await MediaSaver.showMusicPlaybackNotification({
          title: title,
          artist: artist,
          album: album,
          artwork: artwork,
          duration: duration,
          position: position,
          isPlaying: Boolean(isPlaying),
          playbackSpeed: playbackSpeed
        });
      } catch (_) {}
    }
  }

  async clearNativeNotification() {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.clearMusicPlaybackNotification === "function") {
      try {
        await MediaSaver.clearMusicPlaybackNotification();
      } catch (_) {}
    }
  }

  showMiniPlayer() {
    if (this.elements.miniPlayer) {
      this.elements.miniPlayer.classList.remove("hidden");
      document.body.classList.add("mini-player-active");
    }
  }

  hideMiniPlayer() {
    if (this.elements.miniPlayer) {
      this.elements.miniPlayer.classList.add("hidden");
      document.body.classList.remove("mini-player-active");
    }
  }

  minimizeMiniPlayer() {
    this.isCompactMiniPlayer = true;
    if (this.elements.miniPlayer) {
      this.elements.miniPlayer.classList.add("is-compact");
    }
    if (window.triggerHaptic) window.triggerHaptic();
  }

  expandMiniPlayer() {
    this.isCompactMiniPlayer = false;
    if (this.elements.miniPlayer) {
      this.elements.miniPlayer.classList.remove("is-compact");
    }
    if (window.triggerHaptic) window.triggerHaptic();
  }

  openFullPlayer() {
    if (this.elements.fullPlayerModal) {
      this.elements.fullPlayerModal.classList.remove("hidden");
      if (window.triggerHaptic) window.triggerHaptic();
    }
  }

  closeFullPlayer() {
    if (this.elements.fullPlayerModal) {
      this.elements.fullPlayerModal.classList.add("hidden");
    }
  }

  async stopPlayback() {
    this.isStoppingPlayback = true;
    try {
      this.audio.pause();
      this.audio.currentTime = 0;
      this.isPlaying = false;
      this.updatePlaybackUiState(false);
      this.stopVisualizer();
      if ("mediaSession" in navigator) {
        try {
          navigator.mediaSession.playbackState = "none";
        } catch (_) {}
      }
      await this.clearNativeNotification();
      this.hideMiniPlayer();
      this.closeFullPlayer();
      this.expandMiniPlayer();
      this.updateProgressUi(0, 0);
      if (window.triggerHaptic) window.triggerHaptic();
    } catch (err) {
      console.warn("[MUSIC] stopPlayback error:", err);
    } finally {
      setTimeout(() => {
        this.isStoppingPlayback = false;
      }, 300);
    }
  }

  openQueueModal() {
    this.renderQueueModal();
    if (this.elements.queueModal) {
      this.elements.queueModal.classList.remove("hidden");
    }
  }

  closeQueueModal() {
    if (this.elements.queueModal) {
      this.elements.queueModal.classList.add("hidden");
    }
  }

  openLyricsModal() {
    this.renderLyricsModal();
    if (this.elements.lyricsModal) {
      this.elements.lyricsModal.classList.remove("hidden");
    }
  }

  closeLyricsModal() {
    if (this.elements.lyricsModal) {
      this.elements.lyricsModal.classList.add("hidden");
    }
  }


  openSleepTimerModal() {
    if (this.elements.sleepTimerModal) {
      this.elements.sleepTimerModal.classList.remove("hidden");
    }
  }

  closeSleepTimerModal() {
    if (this.elements.sleepTimerModal) {
      this.elements.sleepTimerModal.classList.add("hidden");
    }
  }

  openSpeedModal() {
    if (this.elements.speedModal) {
      if (this.elements.slowedBpmSlider) {
        this.elements.slowedBpmSlider.value = this.bpmRate;
        const pct = ((this.bpmRate - 60) / (140 - 60)) * 100;
        this.elements.slowedBpmSlider.style.setProperty("--progress", `${pct.toFixed(1)}%`);
      }
      if (this.elements.slowedBpmValueLabel) {
        let tag = "Normal";
        if (this.bpmRate < 100) tag = "Slowed";
        else if (this.bpmRate > 100) tag = "SpeedUp";
        this.elements.slowedBpmValueLabel.innerText = `${this.bpmRate}% • ${tag}`;
      }
      if (this.elements.reverbToggle) {
        this.elements.reverbToggle.checked = Boolean(this.isReverbEnabled);
      }
      if (this.elements.reverbPadContainer) {
        this.elements.reverbPadContainer.classList.toggle("hidden", !this.isReverbEnabled);
      }
      if (this.elements.reverbPadPuck) {
        this.elements.reverbPadPuck.style.left = `${(this.reverbDryWet * 100).toFixed(1)}%`;
        this.elements.reverbPadPuck.style.top = `${((1 - this.reverbDepth) * 100).toFixed(1)}%`;
      }
      if (this.elements.reverbStatusLabel) {
        this.elements.reverbStatusLabel.innerText = `Dry/Wet: ${Math.round(this.reverbDryWet * 100)}% • Decay: ${Math.round(this.reverbDepth * 100)}%`;
      }
      this.elements.speedModal.classList.remove("hidden");
    }
  }

  closeSpeedModal() {
    if (this.elements.speedModal) {
      this.elements.speedModal.classList.add("hidden");
    }
  }

  openSortModal() {
    if (this.elements.sortModal) {
      this.elements.sortModal.classList.remove("hidden");
    }
  }

  closeSortModal() {
    if (this.elements.sortModal) {
      this.elements.sortModal.classList.add("hidden");
    }
  }

  openSongInfoModal(track) {
    if (!track) return;
    if (this.elements.infoFieldTitle) this.elements.infoFieldTitle.innerText = track.displayTitle;
    if (this.elements.infoFieldArtist) this.elements.infoFieldArtist.innerText = track.displayArtist;
    if (this.elements.infoFieldAlbum) this.elements.infoFieldAlbum.innerText = track.displayAlbum;
    if (this.elements.infoFieldDuration) {
      this.elements.infoFieldDuration.innerText = track.duration > 0 ? this.formatTime(track.duration / 1000) : "--:--";
    }
    if (this.elements.infoFieldSize) {
      this.elements.infoFieldSize.innerText = track.size ? this.formatFileSize(track.size) : "--";
    }
    if (this.elements.infoFieldFormat) {
      this.elements.infoFieldFormat.innerText = track.mimeType || "audio/mpeg";
    }
    if (this.elements.infoFieldPath) {
      this.elements.infoFieldPath.innerText = track.filePath || track.contentUri || "--";
    }

    if (this.elements.songInfoModal) {
      this.elements.songInfoModal.classList.remove("hidden");
    }
  }

  closeSongInfoModal() {
    if (this.elements.songInfoModal) {
      this.elements.songInfoModal.classList.add("hidden");
    }
  }

  renderQueueModal() {
    if (!this.elements.queueListContainer) return;

    if (this.elements.queueNowPlayingTitle) {
      this.elements.queueNowPlayingTitle.innerText = this.currentTrack ? this.currentTrack.displayTitle : "--";
    }
    if (this.elements.queueNowPlayingArtist) {
      this.elements.queueNowPlayingArtist.innerText = this.currentTrack ? this.currentTrack.displayArtist : "--";
    }

    if (this.queue.length === 0) {
      this.elements.queueListContainer.innerHTML = `
        <div class="music-empty-state">
          <p>${this.t("musicEmptyQueue", "Antrean kosong")}</p>
        </div>
      `;
      return;
    }

    const html = this.queue.map((track, idx) => {
      const isCurrent = idx === this.queueIndex;
      return `
        <div class="queue-item ${isCurrent ? 'active' : ''}" data-queue-idx="${idx}">
          <div class="queue-idx">${isCurrent ? (this.isPlaying ? '⏸' : '▶') : idx + 1}</div>
          <div class="queue-info">
            <span class="queue-title">${this.escapeHtml(track.displayTitle)}</span>
            <span class="queue-artist">${this.escapeHtml(track.displayArtist)}</span>
          </div>
          <button class="icon-btn queue-remove-btn" data-queue-idx="${idx}" title="Remove">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      `;
    }).join("");

    this.elements.queueListContainer.innerHTML = html;

    this.elements.queueListContainer.querySelectorAll(".queue-item").forEach(item => {
      item.addEventListener("click", (e) => {
        if (e.target.closest(".queue-remove-btn")) return;
        const idx = parseInt(item.getAttribute("data-queue-idx"), 10);
        if (!isNaN(idx) && this.queue[idx]) {
          this.queueIndex = idx;
          this.setTrack(this.queue[idx], true);
          this.renderQueueModal();
        }
      });
    });

    this.elements.queueListContainer.querySelectorAll(".queue-remove-btn").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const idx = parseInt(btn.getAttribute("data-queue-idx"), 10);
        if (!isNaN(idx)) {
          this.removeFromQueue(idx);
        }
      });
    });
  }

  // -------------------------------------------------------------
  // Track Actions & Context Menu (3-Dot Bottom Sheet)
  // -------------------------------------------------------------
  showTrackContextMenu(track) {
    if (!track) return;
    const actions = [
      {
        label: this.t("musicPlayNow", "Putar Sekarang"),
        icon: "▶",
        action: () => this.playTrackFromList(track, [track])
      },
      {
        label: this.t("musicPlayNext", "Putar Berikutnya"),
        icon: "⏭",
        action: () => this.playNext(track)
      },
      {
        label: this.t("musicAddToQueue", "Tambah ke Antrean"),
        icon: "➕",
        action: () => this.addToQueue(track)
      },
      {
        label: this.t("musicAddToPlaylist", "Tambah ke Playlist"),
        icon: "📋",
        action: () => this.showAddToPlaylistModal(track)
      },
      {
        label: this.t("musicGoToArtist", "Buka Artis"),
        icon: "👤",
        action: () => {
          const artistObj = this.artistsMap.get(track.displayArtist);
          if (artistObj) {
            this.selectedArtist = artistObj;
            this.renderArtistDrilldown(artistObj);
          }
        }
      },
      {
        label: this.t("musicGoToAlbum", "Buka Album"),
        icon: "💿",
        action: () => {
          const albumKey = `${track.displayAlbum}___${track.displayArtist}`;
          const albObj = this.albumsMap.get(albumKey);
          if (albObj) {
            this.selectedAlbum = albObj;
            this.renderAlbumDrilldown(albObj);
          }
        }
      },
      {
        label: this.t("musicSongInfo", "Rincian Lagu"),
        icon: "ℹ️",
        action: () => this.openSongInfoModal(track)
      },
      {
        label: this.t("musicShareAudio", "Bagikan Audio"),
        icon: "🔗",
        action: () => this.shareTrack(track)
      },
      {
        label: this.t("musicDeleteTrack", "Hapus Berkas"),
        icon: "🗑️",
        danger: true,
        action: () => this.deleteTrackPrompt(track)
      }
    ];

    const menu = document.createElement("div");
    menu.className = "track-context-menu-backdrop";
    menu.innerHTML = `
      <div class="track-context-menu-card">
        <div class="context-menu-header">
          <div class="context-menu-title">${this.escapeHtml(track.displayTitle)}</div>
          <div class="context-menu-artist">${this.escapeHtml(track.displayArtist)}</div>
        </div>
        <div class="context-menu-items">
          ${actions.map((a, i) => `
            <button class="context-menu-btn ${a.danger ? 'danger-action' : ''}" data-action-idx="${i}">
              <span class="context-icon">${a.icon}</span>
              <span class="context-label">${a.label}</span>
            </button>
          `).join("")}
        </div>
        <button class="btn outline-btn full-btn context-cancel-btn">${this.t("btnCancel", "Batal")}</button>
      </div>
    `;

    document.body.appendChild(menu);

    const close = () => {
      menu.classList.add("closing");
      setTimeout(() => menu.remove(), 200);
    };

    menu.addEventListener("click", (e) => {
      if (e.target === menu || e.target.closest(".context-cancel-btn")) {
        close();
      }
    });

    menu.querySelectorAll(".context-menu-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        const idx = parseInt(btn.getAttribute("data-action-idx"), 10);
        if (actions[idx]) actions[idx].action();
        close();
      });
    });
  }

  async shareTrack(track) {
    if (!track) return;
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.shareAudioFile === "function") {
      try {
        await MediaSaver.shareAudioFile({
          filePath: track.filePath || "",
          contentUri: track.contentUri || "",
          title: track.displayTitle,
          mimeType: track.mimeType || "audio/*"
        });
        return;
      } catch (e) {
        console.warn("[MUSIC] Native share failed:", e);
      }
    }

    if (navigator.share) {
      try {
        await navigator.share({
          title: track.displayTitle,
          text: `${track.displayTitle} - ${track.displayArtist}`
        });
      } catch (_) {}
    }
  }

  removeTrackFromDownloadHistory(track) {
    if (!track) return;
    try {
      const historyRaw = localStorage.getItem("nimiyo_download_history_v2");
      if (historyRaw) {
        let items = JSON.parse(historyRaw);
        const origLen = items.length;
        items = items.filter(item => {
          const pathMatch = track.filePath && item.filePath && (track.filePath.toLowerCase() === item.filePath.toLowerCase());
          const nameMatch = track.fileName && item.fileName && (track.fileName.toLowerCase() === item.fileName.toLowerCase());
          const uriMatch = track.contentUri && (item.contentUri === track.contentUri || item.fileUri === track.contentUri);
          return !(pathMatch || nameMatch || uriMatch);
        });
        if (items.length !== origLen) {
          localStorage.setItem("nimiyo_download_history_v2", JSON.stringify(items));
          if (typeof window.renderHistoryList === "function") {
            window.renderHistoryList();
          }
        }
      }
    } catch (_) {}
  }

  promptStoragePermissionModal() {
    const confirmMsg = this.t("manageStoragePermRequired", "Izin Akses Berkas Diperlukan:\n\nPada Android 11 ke atas, Nimiyo memerlukan izin 'Akses semua berkas' agar dapat menghapus atau mengelola berkas audio secara permanen dari memori perangkat.\n\nBuka Setelan sekarang untuk mengaktifkan izin ini?");
    if (confirm(confirmMsg)) {
      const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
      if (MediaSaver && typeof MediaSaver.requestManageStoragePermission === "function") {
        MediaSaver.requestManageStoragePermission();
      }
    }
  }

  async deleteTrackPrompt(track) {
    if (!track) return;
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.checkManageStoragePermission === "function") {
      try {
        const check = await MediaSaver.checkManageStoragePermission();
        if (check && check.granted === false) {
          this.promptStoragePermissionModal();
          return;
        }
      } catch (_) {}
    }
    const confirmMsg = `${this.t("confirmDeleteTrack", "Hapus lagu ini dari penyimpanan perangkat?")}\n\n${track.displayTitle}`;
    if (confirm(confirmMsg)) {
      this.executeDeleteTrack(track);
    }
  }

  async executeDeleteTrack(track) {
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.deleteAudioFile === "function") {
      try {
        const res = await MediaSaver.deleteAudioFile({
          filePath: track.filePath || "",
          contentUri: track.contentUri || "",
          fileName: track.fileName || track.title || "",
          trackId: track.id || ""
        });

        if (res && res.needsAllFilesAccess) {
          this.promptStoragePermissionModal();
          return;
        }

        if (res && res.success) {
          // Remove from local lists
          this.allTracks = this.allTracks.filter(t => t.id !== track.id);
          this.nimiyoTracks = this.nimiyoTracks.filter(t => t.id !== track.id);
          this.removeTrackFromDownloadHistory(track);
          this.processLibraryTracks(this.allTracks);

          // If currently playing, go to next
          if (this.currentTrack && this.currentTrack.id === track.id) {
            this.next();
          }

          this.renderCurrentTab();
          this.updateLibraryStatsUi();

          if (window.showToast) {
            window.showToast(this.t("musicTrackDeleted", "Berkas audio berhasil dihapus"), "success");
          }
          return;
        } else if (res && res.stillExists) {
          if (window.showToast) {
            window.showToast("Gagal menghapus: berkas dilindungi sistem Android", "error");
          }
          return;
        }
      } catch (e) {
        console.warn("[MUSIC] Delete track error:", e);
      }
    }

    // Fallback: Remove from active lists
    this.allTracks = this.allTracks.filter(t => t.id !== track.id);
    this.removeTrackFromDownloadHistory(track);
    this.processLibraryTracks(this.allTracks);
    this.renderCurrentTab();
  }

  // -------------------------------------------------------------
  // Playlist Management Engine
  // -------------------------------------------------------------
  loadPlaylists() {
    try {
      const raw = localStorage.getItem("nimiyo_playlists");
      if (raw) {
        this.playlists = JSON.parse(raw);
      } else {
        this.playlists = [];
      }
    } catch (_) {
      this.playlists = [];
    }
  }

  savePlaylists() {
    try {
      localStorage.setItem("nimiyo_playlists", JSON.stringify(this.playlists));
    } catch (_) {}
  }

  openPlaylistModal() {
    if (this.elements.playlistModal) {
      this.elements.playlistModal.classList.remove("hidden");
      this.closePlaylistDetail();
      this.renderPlaylistsList();
    }
  }

  closePlaylistModal() {
    if (this.elements.playlistModal) {
      this.elements.playlistModal.classList.add("hidden");
    }
  }

  handlePlaylistBack() {
    if (this.elements.playlistDetailView && !this.elements.playlistDetailView.classList.contains("hidden")) {
      this.closePlaylistDetail();
    } else {
      this.closePlaylistModal();
    }
  }

  openCreatePlaylistDialog(editingPlaylistId = null) {
    this.editingPlaylistId = editingPlaylistId;
    const titleEl = this.elements.createPlaylistDialogTitle;
    const inputEl = this.elements.newPlaylistNameInput;
    if (editingPlaylistId) {
      const pl = this.playlists.find(p => p.id === editingPlaylistId);
      if (titleEl) titleEl.innerText = this.t("renamePlaylistTitle", "Ubah Nama Playlist");
      if (inputEl) inputEl.value = pl ? pl.name : "";
    } else {
      if (titleEl) titleEl.innerText = this.t("createPlaylistTitle", "Playlist Baru");
      if (inputEl) inputEl.value = "";
    }
    if (this.elements.createPlaylistModal) {
      this.elements.createPlaylistModal.classList.remove("hidden");
      setTimeout(() => inputEl?.focus(), 150);
    }
  }

  closeCreatePlaylistDialog() {
    if (this.elements.createPlaylistModal) {
      this.elements.createPlaylistModal.classList.add("hidden");
    }
    this.editingPlaylistId = null;
  }

  submitCreatePlaylist() {
    const inputEl = this.elements.newPlaylistNameInput;
    const name = (inputEl?.value || "").trim();
    if (!name) {
      if (window.showToast) window.showToast(this.t("playlistNameRequired", "Nama playlist tidak boleh kosong"), "error");
      return;
    }

    if (this.editingPlaylistId) {
      const pl = this.playlists.find(p => p.id === this.editingPlaylistId);
      if (pl) {
        pl.name = name;
        this.savePlaylists();
        this.renderPlaylistsList();
        if (this.activePlaylistId === pl.id) {
          if (this.elements.playlistDetailName) this.elements.playlistDetailName.innerText = pl.name;
        }
        if (window.showToast) window.showToast(this.t("playlistNameUpdated", "Nama playlist diperbarui"), "success");
      }
    } else {
      const newPl = {
        id: "pl_" + Date.now(),
        name: name,
        createdAt: Date.now(),
        trackIds: []
      };
      this.playlists.unshift(newPl);
      this.savePlaylists();
      this.renderPlaylistsList();
      this.openPlaylistDetail(newPl.id);
      if (window.showToast) window.showToast(this.t("playlistCreatedToast", `Playlist "${name}" dibuat`, { name }), "success");
    }

    this.closeCreatePlaylistDialog();
  }

  renderPlaylistsList() {
    const grid = this.elements.playlistCardsGrid;
    const empty = this.elements.playlistEmptyState;
    const countSub = this.elements.playlistCountSubtitle;
    if (!grid) return;

    if (countSub) {
      countSub.innerText = `${this.playlists.length} ${this.t("playlistCountSubtitle", "Playlist")}`;
    }

    if (this.playlists.length === 0) {
      grid.innerHTML = "";
      if (empty) empty.classList.remove("hidden");
      return;
    }

    if (empty) empty.classList.add("hidden");

    grid.innerHTML = this.playlists.map(pl => {
      let coverSrc = "nimiyo_icon.webp";
      if (pl.trackIds && pl.trackIds.length > 0) {
        const firstTrack = this.allTracks.find(t => t.id === pl.trackIds[0]);
        if (firstTrack) {
          coverSrc = this.artworkCache.get(firstTrack.id) || "nimiyo_icon.webp";
        }
      }
      const count = pl.trackIds ? pl.trackIds.length : 0;
      return `
        <div class="playlist-card" data-playlist-id="${pl.id}">
          <div class="playlist-card-cover-wrap">
            <img class="playlist-card-cover" src="${coverSrc}" alt="Cover" onerror="this.src='nimiyo_icon.webp'">
            <div class="playlist-card-play-overlay">
              <div class="playlist-card-play-btn">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg>
              </div>
            </div>
          </div>
          <div class="playlist-card-info">
            <div class="playlist-card-title">${this.escapeHtml(pl.name)}</div>
            <div class="playlist-card-count">${count} ${this.t("playlistSongsCount", "Lagu")}</div>
          </div>
        </div>
      `;
    }).join("");

    grid.querySelectorAll(".playlist-card").forEach(card => {
      card.addEventListener("click", () => {
        const id = card.getAttribute("data-playlist-id");
        if (id) this.openPlaylistDetail(id);
      });
    });
  }

  openPlaylistDetail(playlistId) {
    const pl = this.playlists.find(p => p.id === playlistId);
    if (!pl) return;

    this.activePlaylistId = playlistId;

    if (this.elements.playlistListView) this.elements.playlistListView.classList.add("hidden");
    if (this.elements.playlistDetailView) this.elements.playlistDetailView.classList.remove("hidden");

    if (this.elements.playlistDetailName) {
      this.elements.playlistDetailName.innerText = pl.name;
    }

    this.renderPlaylistDetailTracks();
  }

  closePlaylistDetail() {
    this.exitPlaylistSelectionMode();
    this.activePlaylistId = null;
    if (this.elements.playlistDetailView) this.elements.playlistDetailView.classList.add("hidden");
    if (this.elements.playlistListView) this.elements.playlistListView.classList.remove("hidden");
    this.renderPlaylistsList();
  }

  renderPlaylistDetailTracks() {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;

    const tracksListEl = this.elements.playlistTracksList;
    const tracksEmptyEl = this.elements.playlistTracksEmpty;
    const coverEl = this.elements.playlistDetailCover;
    const metaEl = this.elements.playlistDetailMeta;

    const trackObjects = [];
    let totalDurMs = 0;

    if (pl.trackIds && pl.trackIds.length > 0) {
      for (const tid of pl.trackIds) {
        const t = this.allTracks.find(item => item.id === tid);
        if (t) {
          trackObjects.push(t);
          totalDurMs += (t.duration || 0);
        }
      }
    }

    if (coverEl) {
      if (trackObjects.length > 0) {
        coverEl.src = this.artworkCache.get(trackObjects[0].id) || "nimiyo_icon.webp";
      } else {
        coverEl.src = "nimiyo_icon.webp";
      }
      coverEl.onerror = () => { coverEl.src = "nimiyo_icon.webp"; };
    }

    if (metaEl) {
      metaEl.innerText = `${trackObjects.length} ${this.t("playlistSongsCount", "Lagu")} • ${this.formatTotalDuration(totalDurMs) || "0 min"}`;
    }

    if (this.elements.playlistSelectBtn) {
      this.elements.playlistSelectBtn.classList.toggle("hidden", trackObjects.length === 0);
      this.elements.playlistSelectBtn.innerText = this.isPlaylistSelectionMode
        ? this.t("playlistBtnCancel", "Selesai")
        : this.t("playlistBtnSelect", "Pilih");
    }

    if (trackObjects.length === 0) {
      this.exitPlaylistSelectionMode();
      if (tracksListEl) tracksListEl.innerHTML = "";
      if (tracksEmptyEl) tracksEmptyEl.classList.remove("hidden");
      return;
    }

    if (tracksEmptyEl) tracksEmptyEl.classList.add("hidden");

    if (tracksListEl) {
      tracksListEl.innerHTML = trackObjects.map((track, idx) => {
        const isCurrent = this.currentTrack && this.currentTrack.id === track.id;
        const isSelected = this.isPlaylistSelectionMode && this.selectedPlaylistTrackIds.has(track.id);
        const durStr = this.formatTime(track.duration ? Math.floor(track.duration / 1000) : 0);
        const artSrc = this.artworkCache.get(track.id) || "nimiyo_icon.webp";
        const indexStr = (idx + 1 < 10 ? "0" : "") + (idx + 1);

        return `
          <div class="playlist-track-item ${isCurrent ? 'active-playing' : ''} ${isSelected ? 'selected' : ''}" data-track-id="${track.id}" data-idx="${idx}">
            <div class="playlist-track-checkbox">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </div>
            <span class="playlist-track-index">${indexStr}</span>
            <div class="playlist-track-thumb-wrap">
              <img class="playlist-track-thumb" src="${artSrc}" alt="Art" onerror="this.src='nimiyo_icon.webp'">
              <div class="playlist-play-overlay ${isCurrent ? 'active' : ''}">
                ${isCurrent && this.isPlaying
                  ? '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>'
                  : '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>'}
              </div>
            </div>
            <div class="playlist-track-info">
              <div class="playlist-track-title">${this.escapeHtml(track.displayTitle)}</div>
              <div class="playlist-track-artist">${this.escapeHtml(track.displayArtist)}</div>
            </div>
            <div class="playlist-track-meta">
              <span class="playlist-track-duration">${durStr}</span>
              <button class="track-item-remove-btn" data-remove-id="${track.id}" aria-label="Remove" title="${this.t("playlistSongRemoved", "Hapus dari Playlist")}">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
              </button>
              <div class="playlist-drag-handle" data-drag-idx="${idx}" title="${this.t("playlistDragReorderHint", "Geser urutan")}">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="4" y1="7" x2="20" y2="7"></line>
                  <line x1="4" y1="12" x2="20" y2="12"></line>
                  <line x1="4" y1="17" x2="20" y2="17"></line>
                </svg>
              </div>
            </div>
          </div>
        `;
      }).join("");

      // Click for play / selection toggle
      tracksListEl.querySelectorAll(".playlist-track-item").forEach(item => {
        const tid = item.getAttribute("data-track-id");
        const track = trackObjects.find(t => t.id === tid);
        if (!track) return;

        item.addEventListener("click", (e) => {
          if (e.target.closest(".track-item-remove-btn") || e.target.closest(".playlist-drag-handle")) return;
          if (this.isPlaylistSelectionMode) {
            this.togglePlaylistTrackSelection(track.id);
            return;
          }
          this.playTrackFromList(track, trackObjects);
        });
      });

      // Click to remove single track from playlist
      tracksListEl.querySelectorAll(".track-item-remove-btn").forEach(btn => {
        btn.addEventListener("click", (e) => {
          e.stopPropagation();
          const tid = btn.getAttribute("data-remove-id");
          if (tid) this.removeTrackFromPlaylist(tid);
        });
      });

      // Initialize Touch & Pointer Drag Reordering
      this.initPlaylistDragReorder(tracksListEl);
    }
  }

  initPlaylistDragReorder(tracksListEl) {
    if (!tracksListEl) return;
    const handles = tracksListEl.querySelectorAll(".playlist-drag-handle");

    handles.forEach(handle => {
      handle.addEventListener("pointerdown", (e) => {
        if (this.isPlaylistSelectionMode) return;
        if (e.button !== 0 && e.pointerType === "mouse") return;
        e.preventDefault();
        e.stopPropagation();

        const item = handle.closest(".playlist-track-item");
        if (!item) return;

        const startY = e.clientY;
        const allItems = Array.from(tracksListEl.querySelectorAll(".playlist-track-item"));

        item.classList.add("is-dragging");
        if (navigator.vibrate) {
          try { navigator.vibrate(25); } catch (_) {}
        }

        let currentOverItem = null;

        const onPointerMove = (moveEv) => {
          moveEv.preventDefault();
          const curY = moveEv.clientY;
          const deltaY = curY - startY;
          item.style.transform = `translateY(${deltaY}px)`;

          allItems.forEach(el => {
            if (el === item) return;
            el.classList.remove("drop-target-above", "drop-target-below");
            const elRect = el.getBoundingClientRect();
            if (curY >= elRect.top && curY <= elRect.bottom) {
              currentOverItem = el;
              if (curY < elRect.top + elRect.height / 2) {
                el.classList.add("drop-target-above");
              } else {
                el.classList.add("drop-target-below");
              }
            }
          });
        };

        const onPointerUp = (upEv) => {
          window.removeEventListener("pointermove", onPointerMove);
          window.removeEventListener("pointerup", onPointerUp);
          window.removeEventListener("pointercancel", onPointerUp);

          item.classList.remove("is-dragging");
          item.style.transform = "";

          allItems.forEach(el => el.classList.remove("drop-target-above", "drop-target-below"));

          if (currentOverItem && currentOverItem !== item) {
            const overRect = currentOverItem.getBoundingClientRect();
            const isAbove = upEv.clientY < overRect.top + overRect.height / 2;
            if (isAbove) {
              tracksListEl.insertBefore(item, currentOverItem);
            } else {
              tracksListEl.insertBefore(item, currentOverItem.nextSibling);
            }

            // Read new order from DOM
            const newTrackIds = Array.from(tracksListEl.querySelectorAll(".playlist-track-item"))
              .map(el => el.getAttribute("data-track-id"))
              .filter(Boolean);

            const pl = this.playlists.find(p => p.id === this.activePlaylistId);
            if (pl) {
              pl.trackIds = newTrackIds;
              this.savePlaylists();
              if (window.triggerHaptic) window.triggerHaptic();

              // Update index numbers in place
              tracksListEl.querySelectorAll(".playlist-track-item").forEach((el, idx) => {
                const idxEl = el.querySelector(".playlist-track-index");
                if (idxEl) {
                  idxEl.innerText = (idx + 1 < 10 ? "0" : "") + (idx + 1);
                }
              });

              if (window.showToast) {
                window.showToast(this.t("playlistReorderDone", "Urutan lagu diperbarui"), "info");
              }
            }
          }
        };

        window.addEventListener("pointermove", onPointerMove, { passive: false });
        window.addEventListener("pointerup", onPointerUp);
        window.addEventListener("pointercancel", onPointerUp);
      });
    });
  }

  togglePlaylistSelectionMode() {
    if (this.isPlaylistSelectionMode) {
      this.exitPlaylistSelectionMode();
    } else {
      this.enterPlaylistSelectionMode();
    }
  }

  enterPlaylistSelectionMode() {
    this.isPlaylistSelectionMode = true;
    this.selectedPlaylistTrackIds.clear();

    const detailEl = this.elements.playlistDetailView;
    if (detailEl) detailEl.classList.add("playlist-selection-active");

    if (this.elements.playlistSelectionBar) {
      this.elements.playlistSelectionBar.classList.remove("hidden");
    }

    if (this.elements.playlistSelectBtn) {
      this.elements.playlistSelectBtn.innerText = this.t("playlistBtnCancel", "Selesai");
    }

    if (window.triggerHaptic) window.triggerHaptic();
    this.updatePlaylistSelectionUi();
  }

  exitPlaylistSelectionMode() {
    this.isPlaylistSelectionMode = false;
    this.selectedPlaylistTrackIds.clear();

    const detailEl = this.elements.playlistDetailView;
    if (detailEl) detailEl.classList.remove("playlist-selection-active");

    if (this.elements.playlistSelectionBar) {
      this.elements.playlistSelectionBar.classList.add("hidden");
    }

    if (this.elements.playlistSelectBtn) {
      this.elements.playlistSelectBtn.innerText = this.t("playlistBtnSelect", "Pilih");
    }

    const removeBtn = this.elements.playlistRemoveSelectedBtn;
    const removeBtnText = document.getElementById("playlistRemoveBtnText");
    if (removeBtnText) {
      removeBtnText.innerText = this.t("playlistActionRemove", "Hapus");
    }
    if (removeBtn) {
      removeBtn.style.opacity = "0.5";
      removeBtn.style.pointerEvents = "none";
    }

    if (this.elements.playlistSelectAllCheckbox) {
      this.elements.playlistSelectAllCheckbox.checked = false;
    }

    if (this.elements.playlistTracksList) {
      this.elements.playlistTracksList.querySelectorAll(".playlist-track-item.selected").forEach(el => {
        el.classList.remove("selected");
      });
    }
  }

  togglePlaylistTrackSelection(trackId) {
    if (this.selectedPlaylistTrackIds.has(trackId)) {
      this.selectedPlaylistTrackIds.delete(trackId);
    } else {
      this.selectedPlaylistTrackIds.add(trackId);
    }
    if (window.triggerHaptic) window.triggerHaptic();
    this.updatePlaylistSelectionUi();
  }

  togglePlaylistSelectAll() {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl || !pl.trackIds || pl.trackIds.length === 0) return;

    const allSelected = pl.trackIds.every(id => this.selectedPlaylistTrackIds.has(id));
    if (allSelected) {
      this.selectedPlaylistTrackIds.clear();
    } else {
      pl.trackIds.forEach(id => this.selectedPlaylistTrackIds.add(id));
    }
    if (window.triggerHaptic) window.triggerHaptic();
    this.updatePlaylistSelectionUi();
  }

  updatePlaylistSelectionUi() {
    const total = this.selectedPlaylistTrackIds.size;
    const badge = this.elements.playlistSelectionCountBadge;
    if (badge) {
      badge.innerText = `${total} ${this.t("pickerSelected", "dipilih")}`;
    }

    const removeBtn = this.elements.playlistRemoveSelectedBtn;
    const removeBtnText = document.getElementById("playlistRemoveBtnText");
    const baseRemoveText = this.t("playlistActionRemove", "Hapus");
    if (removeBtnText) {
      removeBtnText.innerText = total > 0 ? `${baseRemoveText} (${total})` : baseRemoveText;
    }
    if (removeBtn) {
      removeBtn.style.opacity = total > 0 ? "1" : "0.5";
      removeBtn.style.pointerEvents = total > 0 ? "auto" : "none";
    }

    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    const allCount = pl && pl.trackIds ? pl.trackIds.length : 0;
    const isAll = allCount > 0 && pl.trackIds.every(id => this.selectedPlaylistTrackIds.has(id));

    if (this.elements.playlistSelectAllCheckbox) {
      this.elements.playlistSelectAllCheckbox.checked = isAll;
    }

    if (this.elements.playlistTracksList) {
      this.elements.playlistTracksList.querySelectorAll(".playlist-track-item").forEach(item => {
        const tid = item.getAttribute("data-track-id");
        if (tid) {
          item.classList.toggle("selected", this.selectedPlaylistTrackIds.has(tid));
        }
      });
    }
  }

  executePlaylistRemoveSelected() {
    if (this.selectedPlaylistTrackIds.size === 0) {
      if (window.showToast) window.showToast(this.t("selectionMinOneSong", "Pilih minimal 1 lagu terlebih dahulu"), "info");
      return;
    }

    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;

    const count = this.selectedPlaylistTrackIds.size;
    const promptMsg = this.t("playlistConfirmRemoveSelected", `Hapus ${count} lagu dari playlist "${pl.name}"?`, { count, name: pl.name });

    if (confirm(promptMsg)) {
      pl.trackIds = pl.trackIds.filter(id => !this.selectedPlaylistTrackIds.has(id));
      this.savePlaylists();
      this.exitPlaylistSelectionMode();
      this.renderPlaylistDetailTracks();

      if (window.showToast) {
        window.showToast(this.t("playlistTracksRemovedToast", `${count} lagu dihapus dari playlist`, { count }), "success");
      }
    }
  }

  deleteActivePlaylist() {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;
    if (confirm(this.t("confirmDeletePlaylist", `Hapus playlist "${pl.name}"?`, { name: pl.name }))) {
      this.playlists = this.playlists.filter(p => p.id !== this.activePlaylistId);
      this.savePlaylists();
      this.closePlaylistDetail();
      if (window.showToast) window.showToast(this.t("playlistDeletedToast", "Playlist berhasil dihapus"), "success");
    }
  }

  removeTrackFromPlaylist(trackId) {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;
    pl.trackIds = pl.trackIds.filter(id => id !== trackId);
    this.savePlaylists();
    this.renderPlaylistDetailTracks();
    if (window.showToast) window.showToast(this.t("playlistSongRemoved", "Lagu dihapus dari playlist"), "info");
  }

  playActivePlaylist(shuffle = false) {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl || !pl.trackIds || pl.trackIds.length === 0) {
      if (window.showToast) window.showToast(this.t("playlistEmptyToast", "Playlist ini masih kosong"), "info");
      return;
    }

    const trackObjects = [];
    for (const tid of pl.trackIds) {
      const t = this.allTracks.find(item => item.id === tid);
      if (t) trackObjects.push(t);
    }

    if (trackObjects.length === 0) {
      if (window.showToast) window.showToast(this.t("playlistSongNotFound", "Lagu dalam playlist tidak ditemukan"), "error");
      return;
    }

    if (shuffle) {
      this.shuffleAll(trackObjects);
    } else {
      this.playTrackFromList(trackObjects[0], trackObjects);
      if (window.showToast) window.showToast(this.t("playlistPlayingToast", `Memutar: ${pl.name}`, { name: pl.name }), "info");
    }
  }

  // -------------------------------------------------------------
  // Song Picker Engine (Select from all folders: nimiyo/songs/artists/album)
  // -------------------------------------------------------------
  openSongPicker() {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;

    this.selectedPickerTrackIds = new Set(pl.trackIds || []);
    this.pickerActiveTab = "all";
    this.pickerSearchQuery = "";

    if (this.elements.pickerSearchInput) this.elements.pickerSearchInput.value = "";
    if (this.elements.pickerClearSearchBtn) this.elements.pickerClearSearchBtn.classList.add("hidden");

    document.querySelectorAll(".picker-tab-btn[data-picker-tab]").forEach(btn => {
      btn.classList.toggle("active", btn.getAttribute("data-picker-tab") === "all");
    });

    if (this.elements.songPickerModal) {
      this.elements.songPickerModal.classList.remove("hidden");
    }

    this.renderSongPicker();
  }

  closeSongPicker() {
    if (this.elements.songPickerModal) {
      this.elements.songPickerModal.classList.add("hidden");
    }
  }

  getFilteredPickerTracks() {
    let list = [];
    switch (this.pickerActiveTab) {
      case "nimiyo":
        list = [...this.nimiyoTracks];
        break;
      case "artists":
        list = [...this.allTracks].sort((a, b) => (a.displayArtist || "").localeCompare(b.displayArtist || ""));
        break;
      case "album":
        list = [...this.allTracks].sort((a, b) => (a.displayAlbum || "").localeCompare(b.displayAlbum || ""));
        break;
      case "all":
      default:
        list = [...this.allTracks];
        break;
    }

    if (this.pickerSearchQuery) {
      const q = this.pickerSearchQuery.toLowerCase();
      list = list.filter(t =>
        (t.displayTitle && t.displayTitle.toLowerCase().includes(q)) ||
        (t.displayArtist && t.displayArtist.toLowerCase().includes(q)) ||
        (t.displayAlbum && t.displayAlbum.toLowerCase().includes(q))
      );
    }

    return list;
  }

  renderSongPicker() {
    const listEl = this.elements.pickerTrackList;
    const countEl = this.elements.pickerSelectedCount;
    const confirmBtn = this.elements.pickerConfirmBtn;
    const totalEl = this.elements.pickerTotalTracksCount;
    if (!listEl) return;

    const tracks = this.getFilteredPickerTracks();
    const selectedCount = this.selectedPickerTrackIds.size;

    if (countEl) countEl.innerText = `${selectedCount} ${this.t("pickerSelected", "dipilih")}`;
    if (confirmBtn) confirmBtn.innerText = `${this.t("pickerBtnAdd", "Tambahkan")} (${selectedCount})`;
    if (totalEl) totalEl.innerText = `${tracks.length} ${this.t("pickerAvailable", "lagu tersedia")}`;

    if (tracks.length === 0) {
      listEl.innerHTML = `
        <div style="text-align: center; padding: 32px 16px; color: var(--muted-text); font-size: 13px;">
          ${this.t("pickerNoMatch", "Tidak ada lagu yang cocok dengan filter atau pencarian.")}
        </div>
      `;
      return;
    }

    listEl.innerHTML = tracks.map(track => {
      const isSelected = this.selectedPickerTrackIds.has(track.id);
      const artSrc = this.artworkCache.get(track.id) || "nimiyo_icon.webp";
      const durStr = this.formatTime(track.duration ? Math.floor(track.duration / 1000) : 0);
      return `
        <div class="picker-track-item ${isSelected ? 'selected' : ''}" data-track-id="${track.id}">
          <div class="picker-checkbox">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </div>
          <img class="picker-track-thumb" src="${artSrc}" alt="Art" onerror="this.src='nimiyo_icon.webp'">
          <div class="picker-track-info">
            <div class="picker-track-title">${this.escapeHtml(track.displayTitle)}</div>
            <div class="picker-track-artist">${this.escapeHtml(track.displayArtist)}</div>
          </div>
          <span class="picker-track-dur">${durStr}</span>
        </div>
      `;
    }).join("");

    listEl.querySelectorAll(".picker-track-item").forEach(item => {
      item.addEventListener("click", () => {
        const tid = item.getAttribute("data-track-id");
        if (tid) this.togglePickerTrack(tid);
      });
    });
  }

  togglePickerTrack(trackId) {
    if (this.selectedPickerTrackIds.has(trackId)) {
      this.selectedPickerTrackIds.delete(trackId);
    } else {
      this.selectedPickerTrackIds.add(trackId);
    }
    this.renderSongPicker();
  }

  togglePickerSelectAll() {
    const currentTracks = this.getFilteredPickerTracks();
    const allSelected = currentTracks.every(t => this.selectedPickerTrackIds.has(t.id));

    if (allSelected) {
      currentTracks.forEach(t => this.selectedPickerTrackIds.delete(t.id));
    } else {
      currentTracks.forEach(t => this.selectedPickerTrackIds.add(t.id));
    }
    this.renderSongPicker();
  }

  confirmAddSongsToPlaylist() {
    const pl = this.playlists.find(p => p.id === this.activePlaylistId);
    if (!pl) return;

    pl.trackIds = Array.from(this.selectedPickerTrackIds);
    this.savePlaylists();
    this.closeSongPicker();
    this.renderPlaylistDetailTracks();
    if (window.showToast) {
      window.showToast(this.t("playlistUpdatedToast", `Playlist "${pl.name}" diperbarui (${pl.trackIds.length} lagu)`, { name: pl.name, count: pl.trackIds.length }), "success");
    }
  }

  // -------------------------------------------------------------
  // Quick Add Track To Playlist (Context Menu)
  // -------------------------------------------------------------
  showAddToPlaylistModal(track) {
    if (!track) return;
    this.targetTrackForPlaylist = track;

    if (this.elements.addToPlaylistTrackTitle) {
      this.elements.addToPlaylistTrackTitle.innerText = `${track.displayTitle}`;
    }

    const listEl = this.elements.quickPlaylistOptionsList;
    if (listEl) {
      if (this.playlists.length === 0) {
        listEl.innerHTML = `
          <div style="text-align: center; padding: 16px; color: var(--muted-text); font-size: 12px;">
            ${this.t("playlistEmptyTitle", "Belum ada playlist. Buat playlist baru terlebih dahulu.")}
          </div>
        `;
      } else {
        listEl.innerHTML = this.playlists.map(pl => {
          const count = pl.trackIds ? pl.trackIds.length : 0;
          const hasTrack = pl.trackIds && pl.trackIds.includes(track.id);
          return `
            <div class="quick-playlist-item" data-playlist-id="${pl.id}">
              <div class="quick-playlist-item-left">
                <span style="font-size: 18px;">📂</span>
                <div>
                  <div class="quick-playlist-item-title">${this.escapeHtml(pl.name)}</div>
                  <div class="quick-playlist-item-count">${count} ${this.t("playlistSongsCount", "Lagu")}</div>
                </div>
              </div>
              <div>
                ${hasTrack
                  ? '<span style="font-size: 11px; font-weight: 700; color: var(--success-color);">${this.t("quickItemAlreadyAdded", "✓ Sudah ada")}</span>'
                  : '<span style="font-size: 11px; font-weight: 700; color: var(--accent-color);">${this.t("quickItemAdd", "+ Tambah")}</span>'
                }
              </div>
            </div>
          `;
        }).join("");

        listEl.querySelectorAll(".quick-playlist-item").forEach(item => {
          item.addEventListener("click", () => {
            const plId = item.getAttribute("data-playlist-id");
            if (plId) this.addTrackToSpecificPlaylist(plId, track);
          });
        });
      }
    }

    if (this.elements.addToPlaylistModal) {
      this.elements.addToPlaylistModal.classList.remove("hidden");
    }
  }

  closeAddToPlaylistModal() {
    if (this.elements.addToPlaylistModal) {
      this.elements.addToPlaylistModal.classList.add("hidden");
    }
    this.targetTrackForPlaylist = null;
  }

  addTrackToSpecificPlaylist(playlistId, track) {
    const pl = this.playlists.find(p => p.id === playlistId);
    if (!pl) return;

    if (!pl.trackIds) pl.trackIds = [];
    if (pl.trackIds.includes(track.id)) {
      if (window.showToast) window.showToast(this.t("playlistSongAlreadyIn", `Lagu sudah ada di "${pl.name}"`, { name: pl.name }), "info");
    } else {
      pl.trackIds.push(track.id);
      this.savePlaylists();
      if (window.showToast) window.showToast(this.t("playlistSongAddedTo", `Ditambahkan ke "${pl.name}"`, { name: pl.name }), "success");
      if (this.activePlaylistId === pl.id) {
        this.renderPlaylistDetailTracks();
      }
    }
    this.closeAddToPlaylistModal();
  }

  // -------------------------------------------------------------
  // Track Selection Mode Engine
  // -------------------------------------------------------------
  enterSelectionMode(initialTrackId, trackList = []) {
    this.isSelectionMode = true;
    this.currentSelectionTrackList = trackList && trackList.length > 0 ? trackList : (this.activeTab === "nimiyo" ? this.nimiyoTracks : this.allTracks);
    this.selectedTrackIds.clear();
    if (initialTrackId) {
      this.selectedTrackIds.add(initialTrackId);
    }

    document.body.classList.add("selection-mode-active");
    if (this.elements.selectionActionBar) {
      this.elements.selectionActionBar.classList.remove("hidden");
    }

    if (navigator.vibrate) {
      try { navigator.vibrate(40); } catch (_) {}
    }

    this.updateSelectionUi();
  }

  exitSelectionMode() {
    this.isSelectionMode = false;
    this.selectedTrackIds.clear();
    this.currentSelectionTrackList = [];

    document.body.classList.remove("selection-mode-active");
    if (this.elements.selectionActionBar) {
      this.elements.selectionActionBar.classList.add("hidden");
    }

    if (this.elements.selectionAllCheckbox) {
      this.elements.selectionAllCheckbox.checked = false;
    }

    // Unselect all DOM cards
    document.querySelectorAll(".song-card.selected, .track-item.selected").forEach(el => {
      el.classList.remove("selected");
    });
  }

  toggleTrackSelection(trackId) {
    if (this.selectedTrackIds.has(trackId)) {
      this.selectedTrackIds.delete(trackId);
    } else {
      this.selectedTrackIds.add(trackId);
    }
    this.updateSelectionUi();
  }

  updateSelectionUi() {
    const totalSelected = this.selectedTrackIds.size;
    if (this.elements.selectionCountBadge) {
      this.elements.selectionCountBadge.innerText = `${totalSelected} ${this.t("pickerSelected", "dipilih")}`;
    }

    // Check if all displayed tracks are selected
    const allCount = this.currentSelectionTrackList.length;
    const isAllSelected = allCount > 0 && this.currentSelectionTrackList.every(t => this.selectedTrackIds.has(t.id));
    if (this.elements.selectionAllCheckbox) {
      this.elements.selectionAllCheckbox.checked = isAllSelected;
    }

    // Update DOM classes for cards
    document.querySelectorAll(".song-card, .track-item").forEach(card => {
      const tid = card.getAttribute("data-track-id");
      if (tid) {
        card.classList.toggle("selected", this.selectedTrackIds.has(tid));
      }
    });
  }

  toggleSelectAllTracks() {
    if (!this.currentSelectionTrackList || this.currentSelectionTrackList.length === 0) return;

    const isAllSelected = this.currentSelectionTrackList.every(t => this.selectedTrackIds.has(t.id));
    if (isAllSelected) {
      // Uncheck all
      this.selectedTrackIds.clear();
    } else {
      // Check all
      this.currentSelectionTrackList.forEach(t => this.selectedTrackIds.add(t.id));
    }
    this.updateSelectionUi();
  }

  openMoveTargetDialog() {
    if (this.selectedTrackIds.size === 0) {
      if (window.showToast) window.showToast(this.t("selectionMinOneSong", "Pilih minimal 1 lagu terlebih dahulu"), "info");
      return;
    }
    if (this.elements.moveTargetModal) {
      this.elements.moveTargetModal.classList.remove("hidden");
    }
  }

  closeMoveTargetDialog() {
    if (this.elements.moveTargetModal) {
      this.elements.moveTargetModal.classList.add("hidden");
    }
  }

  executeMoveToPlaylistSelection() {
    this.closeMoveTargetDialog();
    const selectedTracks = [];
    for (const tid of this.selectedTrackIds) {
      const t = this.allTracks.find(item => item.id === tid);
      if (t) selectedTracks.push(t);
    }
    if (selectedTracks.length === 0) return;

    // Show playlist picker
    if (this.elements.addToPlaylistTrackTitle) {
      this.elements.addToPlaylistTrackTitle.innerText = `${selectedTracks.length} ${this.t("pickerSelected", "lagu dipilih")}`;
    }

    const listEl = this.elements.quickPlaylistOptionsList;
    if (listEl) {
      if (this.playlists.length === 0) {
        listEl.innerHTML = `
          <div style="text-align: center; padding: 16px; color: var(--muted-text); font-size: 12px;">
            Belum ada playlist. Buat playlist baru terlebih dahulu.
          </div>
        `;
      } else {
        listEl.innerHTML = this.playlists.map(pl => {
          const count = pl.trackIds ? pl.trackIds.length : 0;
          return `
            <div class="quick-playlist-item" data-playlist-id="${pl.id}">
              <div class="quick-playlist-item-left">
                <span style="font-size: 18px;">📂</span>
                <div>
                  <div class="quick-playlist-item-title">${this.escapeHtml(pl.name)}</div>
                  <div class="quick-playlist-item-count">${count} Lagu</div>
                </div>
              </div>
              <span style="font-size: 11px; font-weight: 700; color: var(--accent-color);">+ Tambah (${selectedTracks.length})</span>
            </div>
          `;
        }).join("");

        listEl.querySelectorAll(".quick-playlist-item").forEach(item => {
          item.addEventListener("click", () => {
            const plId = item.getAttribute("data-playlist-id");
            if (plId) {
              const targetPl = this.playlists.find(p => p.id === plId);
              if (targetPl) {
                if (!targetPl.trackIds) targetPl.trackIds = [];
                let addedCount = 0;
                selectedTracks.forEach(t => {
                  if (!targetPl.trackIds.includes(t.id)) {
                    targetPl.trackIds.push(t.id);
                    addedCount++;
                  }
                });
                this.savePlaylists();
                if (window.showToast) {
                  window.showToast(this.t("selectionAddedToPlaylistToast", `${addedCount} lagu ditambahkan ke "${targetPl.name}"`, { count: addedCount, name: targetPl.name }), "success");
                }
                if (this.activePlaylistId === targetPl.id) {
                  this.renderPlaylistDetailTracks();
                }
              }
              this.closeAddToPlaylistModal();
              this.exitSelectionMode();
            }
          });
        });
      }
    }

    if (this.elements.addToPlaylistModal) {
      this.elements.addToPlaylistModal.classList.remove("hidden");
    }
  }

  async executeMoveToNimiyoSelection() {
    this.closeMoveTargetDialog();
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    const selectedTracks = [];
    for (const tid of this.selectedTrackIds) {
      const t = this.allTracks.find(item => item.id === tid);
      if (t) selectedTracks.push(t);
    }
    if (selectedTracks.length === 0) return;

    let movedCount = 0;
    let alreadyCount = 0;

    for (const track of selectedTracks) {
      if (track.isNimiyo || (track.filePath && track.filePath.toLowerCase().includes("nimiyo"))) {
        alreadyCount++;
        continue;
      }

      if (MediaSaver && typeof MediaSaver.moveAudioToNimiyo === "function") {
        try {
          const res = await MediaSaver.moveAudioToNimiyo({
            filePath: track.filePath || "",
            contentUri: track.contentUri || "",
            fileName: track.fileName || `${track.displayTitle}.mp3`
          });
          if (res && res.success) {
            track.filePath = res.newPath || track.filePath;
            track.isNimiyo = true;
            if (!this.nimiyoTracks.some(t => t.id === track.id)) {
              this.nimiyoTracks.push(track);
            }
            movedCount++;
          }
        } catch (e) {
          console.warn("[MUSIC] Move to Nimiyo failed for track:", track.displayTitle, e);
        }
      } else {
        track.isNimiyo = true;
        if (!this.nimiyoTracks.some(t => t.id === track.id)) {
          this.nimiyoTracks.push(track);
        }
        movedCount++;
      }
    }

    this.exitSelectionMode();
    this.renderCurrentTab();
    this.updateLibraryStatsUi();

    if (window.showToast) {
      if (movedCount > 0) {
        window.showToast(this.t("selectionMovedToNimiyoToast", `${movedCount} lagu berhasil dipindahkan ke folder Nimiyo`, { count: movedCount }), "success");
      } else if (alreadyCount > 0) {
        window.showToast(this.t("selectionAlreadyInNimiyoToast", "Semua lagu yang dipilih sudah ada di folder Nimiyo"), "info");
      }
    }
  }

  async openDeleteConfirmDialog() {
    if (this.selectedTrackIds.size === 0) {
      if (window.showToast) window.showToast("Pilih minimal 1 lagu terlebih dahulu", "info");
      return;
    }
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    if (MediaSaver && typeof MediaSaver.checkManageStoragePermission === "function") {
      try {
        const check = await MediaSaver.checkManageStoragePermission();
        if (check && check.granted === false) {
          this.promptStoragePermissionModal();
          return;
        }
      } catch (_) {}
    }
    if (this.elements.deleteConfirmMessage) {
      const count = this.selectedTrackIds.size;
      this.elements.deleteConfirmMessage.innerText = this.t("deleteConfirmPrompt", `Apakah anda ingin menghapus permanent ${count} lagu atau Audio yang di pilih? ini akan menghilangkan Berkas Audio tersebut.`, { count });
    }
    if (this.elements.deleteConfirmModal) {
      this.elements.deleteConfirmModal.classList.remove("hidden");
    }
  }

  closeDeleteConfirmDialog() {
    if (this.elements.deleteConfirmModal) {
      this.elements.deleteConfirmModal.classList.add("hidden");
    }
  }

  async executePermanentDeleteSelection() {
    this.closeDeleteConfirmDialog();
    const MediaSaver = window.Capacitor?.Plugins?.MediaSaver;
    const idsToDelete = Array.from(this.selectedTrackIds);
    let deletedCount = 0;
    let needsPermission = false;

    for (const tid of idsToDelete) {
      const track = this.allTracks.find(t => t.id === tid);
      if (track) {
        let deletedSuccessfully = true;
        if (MediaSaver && typeof MediaSaver.deleteAudioFile === "function") {
          try {
            const res = await MediaSaver.deleteAudioFile({
              filePath: track.filePath || "",
              contentUri: track.contentUri || "",
              fileName: track.fileName || track.title || "",
              trackId: track.id || ""
            });

            if (res && res.needsAllFilesAccess) {
              needsPermission = true;
              deletedSuccessfully = false;
            } else if (res && res.stillExists) {
              deletedSuccessfully = false;
            }
          } catch (e) {
            console.warn("[MUSIC] Delete failed for:", track.displayTitle, e);
            deletedSuccessfully = false;
          }
        }

        if (deletedSuccessfully) {
          // Remove from memory
          this.allTracks = this.allTracks.filter(t => t.id !== tid);
          this.nimiyoTracks = this.nimiyoTracks.filter(t => t.id !== tid);
          this.removeTrackFromDownloadHistory(track);

          // Remove from playlists
          this.playlists.forEach(pl => {
            if (pl.trackIds) {
              pl.trackIds = pl.trackIds.filter(id => id !== tid);
            }
          });

          // If current track is deleted
          if (this.currentTrack && this.currentTrack.id === tid) {
            this.next();
          }

          deletedCount++;
        }
      }
    }

    this.savePlaylists();
    this.processLibraryTracks(this.allTracks);
    this.exitSelectionMode();
    this.renderCurrentTab();
    if (this.activePlaylistId) {
      this.renderPlaylistDetailTracks();
    }
    this.updateLibraryStatsUi();

    if (needsPermission) {
      this.promptStoragePermissionModal();
    } else if (deletedCount > 0) {
      if (window.showToast) {
        window.showToast(this.t("selectionPermanentDeletedToast", `${deletedCount} berkas audio berhasil dihapus permanent`, { count: deletedCount }), "success");
      }
    } else {
      if (window.showToast) {
        window.showToast(this.t("selectionNoFilesDeletedToast", "Tidak ada berkas yang terhapus"), "warning");
      }
    }
  }

  bindTrackSelectionGestures(element, track, trackList) {
    let pressTimer = null;
    let startX = 0;
    let startY = 0;
    let isPressTriggered = false;

    const clearTimer = () => {
      if (pressTimer) {
        clearTimeout(pressTimer);
        pressTimer = null;
      }
    };

    element.addEventListener("pointerdown", (e) => {
      if (e.target.closest(".song-more-btn") || e.target.closest(".track-item-remove-btn")) return;
      startX = e.clientX;
      startY = e.clientY;
      isPressTriggered = false;

      clearTimer();
      pressTimer = setTimeout(() => {
        isPressTriggered = true;
        if (!this.isSelectionMode) {
          this.enterSelectionMode(track.id, trackList);
        } else {
          this.toggleTrackSelection(track.id);
        }
      }, 450);
    }, { passive: true });

    element.addEventListener("pointermove", (e) => {
      if (!pressTimer) return;
      const dx = Math.abs(e.clientX - startX);
      const dy = Math.abs(e.clientY - startY);
      if (dx > 10 || dy > 10) {
        clearTimer();
      }
    }, { passive: true });

    element.addEventListener("pointerup", () => {
      clearTimer();
    }, { passive: true });

    element.addEventListener("pointercancel", () => {
      clearTimer();
    }, { passive: true });

    element.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      clearTimer();
      if (!this.isSelectionMode) {
        this.enterSelectionMode(track.id, trackList);
      }
    });

    element.addEventListener("click", (e) => {
      if (e.target.closest(".song-more-btn") || e.target.closest(".track-item-remove-btn")) return;
      if (isPressTriggered) {
        isPressTriggered = false;
        e.stopImmediatePropagation();
        return;
      }
      if (this.isSelectionMode) {
        e.stopImmediatePropagation();
        this.toggleTrackSelection(track.id);
      }
    }, true);
  }

  // -------------------------------------------------------------
  // UI Event Listeners
  // -------------------------------------------------------------
  setupUiEventListeners() {
    // Sub-tab Navigation (NIMIYO | SONGS | ARTISTS | ALBUM)
    document.querySelectorAll(".player-tab-btn[data-subtab]").forEach(btn => {
      btn.addEventListener("click", () => {
        const subtab = btn.getAttribute("data-subtab");
        if (subtab) this.switchSubTab(subtab);
      });
    });

    window.addEventListener("resize", () => {
      this.updateSubTabIndicator();
    });

    // Sub-header back button
    if (this.elements.backSubHeaderBtn) {
      this.elements.backSubHeaderBtn.addEventListener("click", () => {
        this.closeDrilldown();
      });
    }

    // Quick Action: Shuffle All
    if (this.elements.shuffleAllBtn) {
      this.elements.shuffleAllBtn.addEventListener("click", () => {
        this.shuffleAll();
      });
    }

    // Quick Action: Sort Order Modal
    if (this.elements.sortBtn) {
      this.elements.sortBtn.addEventListener("click", () => {
        this.openSortModal();
      });
    }

    // Sort options selection
    if (this.elements.sortModal) {
      this.elements.sortModal.querySelectorAll(".sort-option-item").forEach(item => {
        item.addEventListener("click", () => {
          const sortVal = item.getAttribute("data-sort");
          if (sortVal) this.setSortMode(sortVal);
        });
      });
      if (this.elements.closeSortModalBtn) {
        this.elements.closeSortModalBtn.addEventListener("click", () => this.closeSortModal());
      }
    }

    // Search input
    if (this.elements.searchInput) {
      this.elements.searchInput.addEventListener("input", (e) => {
        const val = e.target.value || "";
        if (this.elements.clearSearchBtn) {
          this.elements.clearSearchBtn.classList.toggle("hidden", val.length === 0);
        }
        this.renderCurrentTab();
      });
    }

    if (this.elements.clearSearchBtn) {
      this.elements.clearSearchBtn.addEventListener("click", () => {
        if (this.elements.searchInput) {
          this.elements.searchInput.value = "";
          this.elements.clearSearchBtn.classList.add("hidden");
          this.renderCurrentTab();
        }
      });
    }

    // Reload library button
    if (this.elements.reloadLibraryBtn) {
      this.elements.reloadLibraryBtn.addEventListener("click", () => this.refreshLibrary());
    }

    // Permission Grant Button
    if (this.elements.grantPermissionBtn) {
      this.elements.grantPermissionBtn.addEventListener("click", () => this.requestAudioPermission());
    }

    // Mini Player Controls
    if (this.elements.miniPlayer) {
      this.elements.miniPlayer.addEventListener("click", (e) => {
        if (e.target.closest(".mini-player-btn") || e.target.closest(".compact-square-play-btn")) return;
        if (this.isCompactMiniPlayer) {
          this.expandMiniPlayer();
          return;
        }
        this.openFullPlayer();
      });
    }

    if (this.elements.miniPlayerPlayBtn) {
      this.elements.miniPlayerPlayBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.togglePlay();
      });
    }

    if (this.elements.miniPlayerMinimizeBtn) {
      this.elements.miniPlayerMinimizeBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.minimizeMiniPlayer();
      });
    }

    if (this.elements.miniPlayerCloseBtn) {
      this.elements.miniPlayerCloseBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.stopPlayback();
      });
    }

    if (this.elements.miniPlayerCompactPlayBtn) {
      this.elements.miniPlayerCompactPlayBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.togglePlay();
      });
    }

    // Full Player Controls
    if (this.elements.closeFullPlayerBtn) {
      this.elements.closeFullPlayerBtn.addEventListener("click", () => this.closeFullPlayer());
    }

    if (this.elements.fullPlayerPlayBtn) {
      this.elements.fullPlayerPlayBtn.addEventListener("click", () => this.togglePlay());
    }

    if (this.elements.fullPlayerPrevBtn) {
      this.elements.fullPlayerPrevBtn.addEventListener("click", () => this.previous());
    }

    if (this.elements.fullPlayerNextBtn) {
      this.elements.fullPlayerNextBtn.addEventListener("click", () => this.next());
    }

    if (this.elements.fullPlayerShuffleBtn) {
      this.elements.fullPlayerShuffleBtn.addEventListener("click", () => this.toggleShuffle());
    }

    if (this.elements.fullPlayerRepeatBtn) {
      this.elements.fullPlayerRepeatBtn.addEventListener("click", () => this.cycleRepeat());
    }

    if (this.elements.fullPlayerProgressBar) {
      this.elements.fullPlayerProgressBar.addEventListener("input", (e) => {
        const val = parseFloat(e.target.value);
        const max = parseFloat(e.target.max) || 100;
        const pct = max > 0 ? (val / max) * 100 : 0;
        e.target.style.setProperty("--progress", `${pct.toFixed(2)}%`);
        if (!isNaN(val)) this.seek(val);
      });
    }

    // Clickable Duration toggle
    if (this.elements.fullPlayerDuration) {
      this.elements.fullPlayerDuration.addEventListener("click", () => {
        this.toggleDurationDisplayMode();
      });
    }

    // Volume Slider
    if (this.elements.fullPlayerVolumeSlider) {
      this.elements.fullPlayerVolumeSlider.addEventListener("input", (e) => {
        const val = parseFloat(e.target.value) / 100;
        e.target.style.setProperty("--progress", `${Math.round(val * 100)}%`);
        this.setVolume(val);
      });
    }

    // Volume Icon (Mute / Unmute)
    if (this.elements.fullPlayerVolumeIconBtn) {
      this.elements.fullPlayerVolumeIconBtn.addEventListener("click", () => {
        this.toggleMute();
      });
    }

    // Tap Album Artwork to Open Full-Stage Flowing Lyrics
    if (this.elements.fullPlayerArtCard) {
      this.elements.fullPlayerArtCard.addEventListener("click", () => {
        this.toggleLyricsMode(true);
      });
    }

    if (this.elements.fullPlayerLyricsBadge) {
      this.elements.fullPlayerLyricsBadge.addEventListener("click", () => {
        this.toggleLyricsMode(true);
      });
    }

    // Toggle Back to Album Cover from Lyrics Stage
    if (this.elements.toggleLyricsCoverBtn) {
      this.elements.toggleLyricsCoverBtn.addEventListener("click", () => {
        this.toggleLyricsMode(false);
      });
    }

    if (this.elements.lyricsMiniTrackInfo) {
      this.elements.lyricsMiniTrackInfo.addEventListener("click", () => {
        this.toggleLyricsMode(false);
      });
    }

    // Edit Lyrics Modal Action Listeners
    if (this.elements.openEditLyricsBtn) {
      this.elements.openEditLyricsBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        this.openEditLyricsModal();
      });
    }

    if (this.elements.closeEditLyricsModalBtn) {
      this.elements.closeEditLyricsModalBtn.addEventListener("click", () => {
        this.closeEditLyricsModal();
      });
    }

    if (this.elements.cancelEditLyricsBtn) {
      this.elements.cancelEditLyricsBtn.addEventListener("click", () => {
        this.closeEditLyricsModal();
      });
    }

    if (this.elements.saveEditLyricsBtn) {
      this.elements.saveEditLyricsBtn.addEventListener("click", () => {
        this.saveEditedLyrics();
      });
    }

    if (this.elements.downloadLyricsFileBtn) {
      this.elements.downloadLyricsFileBtn.addEventListener("click", () => {
        this.downloadLyricsFile();
      });
    }

    if (this.elements.editLyricsModal) {
      this.elements.editLyricsModal.addEventListener("click", (e) => {
        if (e.target === this.elements.editLyricsModal) {
          this.closeEditLyricsModal();
        }
      });
    }

    // Refresh & Reset Lyrics Actions
    if (this.elements.refreshLyricsBtn) {
      this.elements.refreshLyricsBtn.addEventListener("click", () => {
        this.openResetLyricsModal();
      });
    }

    if (this.elements.closeResetLyricsModalBtn) {
      this.elements.closeResetLyricsModalBtn.addEventListener("click", () => {
        this.closeResetLyricsModal();
      });
    }

    if (this.elements.resetLyricsModal) {
      this.elements.resetLyricsModal.addEventListener("click", (e) => {
        if (e.target === this.elements.resetLyricsModal) {
          this.closeResetLyricsModal();
        }
      });
    }

    if (this.elements.resetLyricsDefaultBtn) {
      this.elements.resetLyricsDefaultBtn.addEventListener("click", () => {
        this.handleResetLyricsDefault();
      });
    }

    if (this.elements.resetLyricsLyricsifyBtn) {
      this.elements.resetLyricsLyricsifyBtn.addEventListener("click", () => {
        this.handleResetLyricsLyricsify();
      });
    }

    // Import Lyrics Actions (.LRC / .TXT)
    if (this.elements.importLyricsBtn) {
      this.elements.importLyricsBtn.addEventListener("click", () => {
        this.elements.importLrcFileInput?.click();
      });
    }

    if (this.elements.resetLyricsImportBtn) {
      this.elements.resetLyricsImportBtn.addEventListener("click", () => {
        this.elements.importLrcFileInput?.click();
      });
    }

    if (this.elements.importLrcFileInput) {
      this.elements.importLrcFileInput.addEventListener("change", (e) => {
        this.handleImportLyricsFile(e);
      });
    }

    // Secondary Actions (Sleep Timer, Speed, Queue, More Options)
    if (this.elements.fullPlayerSleepTimerBtn) {
      this.elements.fullPlayerSleepTimerBtn.addEventListener("click", () => this.openSleepTimerModal());
    }

    if (this.elements.fullPlayerSpeedBtn) {
      this.elements.fullPlayerSpeedBtn.addEventListener("click", () => this.openSpeedModal());
    }

    if (this.elements.fullPlayerQueueBtn) {
      this.elements.fullPlayerQueueBtn.addEventListener("click", () => this.openQueueModal());
    }

    if (this.elements.fullPlayerMoreBtn) {
      this.elements.fullPlayerMoreBtn.addEventListener("click", () => {
        if (this.currentTrack) {
          this.showTrackContextMenu(this.currentTrack);
        }
      });
    }

    // Sleep Timer Modal Preset Buttons
    if (this.elements.sleepTimerModal) {
      this.elements.sleepTimerModal.querySelectorAll(".option-pill-btn").forEach(btn => {
        btn.addEventListener("click", () => {
          const min = btn.getAttribute("data-minutes");
          this.setSleepTimer(min);
        });
      });
      if (this.elements.setCustomTimerBtn && this.elements.customTimerInput) {
        this.elements.setCustomTimerBtn.addEventListener("click", () => {
          const val = this.elements.customTimerInput.value;
          if (val) this.setSleepTimer(val);
        });
      }
      if (this.elements.closeSleepTimerModalBtn) {
        this.elements.closeSleepTimerModalBtn.addEventListener("click", () => this.closeSleepTimerModal());
      }
    }

    // MixTools Modal Events (BPM Slider & Spatial Reverb)
    if (this.elements.speedModal) {
      if (this.elements.slowedBpmSlider) {
        this.elements.slowedBpmSlider.addEventListener("input", (e) => {
          const bpm = parseInt(e.target.value, 10);
          this.setBpmRate(bpm, false);
        });
      }
      if (this.elements.resetSlowedBpmBtn) {
        this.elements.resetSlowedBpmBtn.addEventListener("click", () => {
          this.setBpmRate(100, true);
        });
      }
      if (this.elements.reverbToggle) {
        this.elements.reverbToggle.addEventListener("change", (e) => {
          this.toggleReverbMode(e.target.checked);
        });
      }
      if (this.elements.resetReverbBtn) {
        this.elements.resetReverbBtn.addEventListener("click", () => {
          this.setReverbXY(0.5, 0.5);
        });
      }
      if (this.elements.resetAllMixToolsBtn) {
        this.elements.resetAllMixToolsBtn.addEventListener("click", () => {
          this.resetAllMixTools();
        });
      }
      this.setupReverbPadEvents();

      if (this.elements.closeSpeedModalBtn) {
        this.elements.closeSpeedModalBtn.addEventListener("click", () => this.closeSpeedModal());
      }
    }

    // Song Info Modal Close
    if (this.elements.closeSongInfoModalBtn) {
      this.elements.closeSongInfoModalBtn.addEventListener("click", () => this.closeSongInfoModal());
    }

    // Modal Close buttons
    if (this.elements.closeQueueModalBtn) {
      this.elements.closeQueueModalBtn.addEventListener("click", () => this.closeQueueModal());
    }
    if (this.elements.clearQueueBtn) {
      this.elements.clearQueueBtn.addEventListener("click", () => this.clearQueue());
    }

    if (this.elements.closeLyricsModalBtn) {
      this.elements.closeLyricsModalBtn.addEventListener("click", () => this.closeLyricsModal());
    }

    // ============================================================
    // Playlist & Song Picker Event Listeners
    // ============================================================
    if (this.elements.openPlaylistBtn) {
      this.elements.openPlaylistBtn.addEventListener("click", () => this.openPlaylistModal());
    }

    if (this.elements.playlistBackBtn) {
      this.elements.playlistBackBtn.addEventListener("click", () => this.handlePlaylistBack());
    }

    if (this.elements.playlistCreateBtn) {
      this.elements.playlistCreateBtn.addEventListener("click", () => this.openCreatePlaylistDialog());
    }
    if (this.elements.playlistEmptyCreateBtn) {
      this.elements.playlistEmptyCreateBtn.addEventListener("click", () => this.openCreatePlaylistDialog());
    }

    if (this.elements.playlistPlayAllBtn) {
      this.elements.playlistPlayAllBtn.addEventListener("click", () => this.playActivePlaylist(false));
    }
    if (this.elements.playlistShuffleBtn) {
      this.elements.playlistShuffleBtn.addEventListener("click", () => this.playActivePlaylist(true));
    }
    if (this.elements.playlistAddSongsBtn) {
      this.elements.playlistAddSongsBtn.addEventListener("click", () => this.openSongPicker());
    }
    if (this.elements.playlistTracksEmptyAddBtn) {
      this.elements.playlistTracksEmptyAddBtn.addEventListener("click", () => this.openSongPicker());
    }
    if (this.elements.playlistSelectBtn) {
      this.elements.playlistSelectBtn.addEventListener("click", () => this.togglePlaylistSelectionMode());
    }
    if (this.elements.playlistSelectAllCheckbox) {
      this.elements.playlistSelectAllCheckbox.addEventListener("change", () => this.togglePlaylistSelectAll());
    }
    if (this.elements.playlistRemoveSelectedBtn) {
      this.elements.playlistRemoveSelectedBtn.addEventListener("click", () => this.executePlaylistRemoveSelected());
    }
    if (this.elements.playlistCancelSelectionBtn) {
      this.elements.playlistCancelSelectionBtn.addEventListener("click", () => this.exitPlaylistSelectionMode());
    }

    if (this.elements.playlistDeleteBtn) {
      this.elements.playlistDeleteBtn.addEventListener("click", () => this.deleteActivePlaylist());
    }

    // Song Picker Dialog Actions
    if (this.elements.closeSongPickerBtn) {
      this.elements.closeSongPickerBtn.addEventListener("click", () => this.closeSongPicker());
    }
    if (this.elements.pickerCancelBtn) {
      this.elements.pickerCancelBtn.addEventListener("click", () => this.closeSongPicker());
    }
    if (this.elements.pickerConfirmBtn) {
      this.elements.pickerConfirmBtn.addEventListener("click", () => this.confirmAddSongsToPlaylist());
    }
    if (this.elements.pickerSelectAllBtn) {
      this.elements.pickerSelectAllBtn.addEventListener("click", () => this.togglePickerSelectAll());
    }

    // Picker Filter Sub-Tabs (SEMUA | NIMIYO | ARTIS | ALBUM)
    document.querySelectorAll(".picker-tab-btn[data-picker-tab]").forEach(btn => {
      btn.addEventListener("click", () => {
        const tab = btn.getAttribute("data-picker-tab");
        if (tab) {
          this.pickerActiveTab = tab;
          document.querySelectorAll(".picker-tab-btn[data-picker-tab]").forEach(b => {
            b.classList.toggle("active", b === btn);
          });
          this.renderSongPicker();
        }
      });
    });

    // Picker Search Input
    if (this.elements.pickerSearchInput) {
      this.elements.pickerSearchInput.addEventListener("input", (e) => {
        const val = e.target.value || "";
        this.pickerSearchQuery = val;
        if (this.elements.pickerClearSearchBtn) {
          this.elements.pickerClearSearchBtn.classList.toggle("hidden", val.length === 0);
        }
        this.renderSongPicker();
      });
    }

    if (this.elements.pickerClearSearchBtn) {
      this.elements.pickerClearSearchBtn.addEventListener("click", () => {
        if (this.elements.pickerSearchInput) {
          this.elements.pickerSearchInput.value = "";
          this.pickerSearchQuery = "";
          this.elements.pickerClearSearchBtn.classList.add("hidden");
          this.renderSongPicker();
        }
      });
    }

    // Create Playlist Dialog Actions
    if (this.elements.closeCreatePlaylistBtn) {
      this.elements.closeCreatePlaylistBtn.addEventListener("click", () => this.closeCreatePlaylistDialog());
    }
    if (this.elements.cancelCreatePlaylistBtn) {
      this.elements.cancelCreatePlaylistBtn.addEventListener("click", () => this.closeCreatePlaylistDialog());
    }
    if (this.elements.submitCreatePlaylistBtn) {
      this.elements.submitCreatePlaylistBtn.addEventListener("click", () => this.submitCreatePlaylist());
    }
    if (this.elements.newPlaylistNameInput) {
      this.elements.newPlaylistNameInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          this.submitCreatePlaylist();
        }
      });
    }

    // Add To Playlist Modal (from Track Context Menu) Actions
    if (this.elements.closeAddToPlaylistBtn) {
      this.elements.closeAddToPlaylistBtn.addEventListener("click", () => this.closeAddToPlaylistModal());
    }
    if (this.elements.quickCreatePlaylistBtn) {
      this.elements.quickCreatePlaylistBtn.addEventListener("click", () => {
        this.closeAddToPlaylistModal();
        this.openCreatePlaylistDialog();
      });
    }

    // Close on overlay backdrop tap
    [
      this.elements.queueModal,
      this.elements.lyricsModal,
      this.elements.sleepTimerModal,
      this.elements.speedModal,
      this.elements.songInfoModal,
      this.elements.sortModal,
      this.elements.songPickerModal,
      this.elements.createPlaylistModal,
      this.elements.addToPlaylistModal,
      this.elements.moveTargetModal,
      this.elements.deleteConfirmModal
    ].forEach(modal => {
      if (modal) {
        modal.addEventListener("click", (e) => {
          if (e.target === modal) modal.classList.add("hidden");
        });
      }
    });

    // Selection Mode Action Bar Events
    if (this.elements.selectionAllCheckbox) {
      this.elements.selectionAllCheckbox.addEventListener("change", () => this.toggleSelectAllTracks());
    }
    if (this.elements.selectionMoveBtn) {
      this.elements.selectionMoveBtn.addEventListener("click", () => this.openMoveTargetDialog());
    }
    if (this.elements.selectionDeleteBtn) {
      this.elements.selectionDeleteBtn.addEventListener("click", () => this.openDeleteConfirmDialog());
    }
    if (this.elements.selectionCancelBtn) {
      this.elements.selectionCancelBtn.addEventListener("click", () => this.exitSelectionMode());
    }

    // Move Target Modal Events
    if (this.elements.closeMoveTargetBtn) {
      this.elements.closeMoveTargetBtn.addEventListener("click", () => this.closeMoveTargetDialog());
    }
    if (this.elements.moveOptionPlaylistBtn) {
      this.elements.moveOptionPlaylistBtn.addEventListener("click", () => this.executeMoveToPlaylistSelection());
    }
    if (this.elements.moveOptionNimiyoBtn) {
      this.elements.moveOptionNimiyoBtn.addEventListener("click", () => this.executeMoveToNimiyoSelection());
    }

    // Delete Confirm Modal Events
    if (this.elements.closeDeleteModalBtn) {
      this.elements.closeDeleteModalBtn.addEventListener("click", () => this.closeDeleteConfirmDialog());
    }
    if (this.elements.cancelDeleteModalBtn) {
      this.elements.cancelDeleteModalBtn.addEventListener("click", () => this.closeDeleteConfirmDialog());
    }
    if (this.elements.confirmDeleteModalBtn) {
      this.elements.confirmDeleteModalBtn.addEventListener("click", () => this.executePermanentDeleteSelection());
    }
  }

  // -------------------------------------------------------------
  // Helpers & Localization
  // -------------------------------------------------------------
  formatTime(seconds) {
    if (isNaN(seconds) || seconds < 0) return "00:00";
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  }

  formatTotalDuration(ms) {
    if (!ms || ms <= 0) return "";
    const totalSec = Math.floor(ms / 1000);
    const mins = Math.floor(totalSec / 60);
    const hrs = Math.floor(mins / 60);
    if (hrs > 0) {
      return `${hrs} hr ${mins % 60} min`;
    }
    return `${mins} min`;
  }

  formatFileSize(bytes) {
    if (!bytes || bytes <= 0) return "--";
    const units = ["B", "KB", "MB", "GB"];
    let size = bytes;
    let unitIdx = 0;
    while (size >= 1024 && unitIdx < units.length - 1) {
      size /= 1024;
      unitIdx++;
    }
    return `${size.toFixed(1)} ${units[unitIdx]}`;
  }

  escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  t(key, fallback = "", params = {}) {
    if (typeof window.getTranslation === "function") {
      const val = window.getTranslation(key, params);
      if (val && val !== key) return val;
    }
    if (params && typeof params === "object") {
      let text = fallback;
      Object.keys(params).forEach(p => {
        text = text.replace(new RegExp(`\\{${p}\\}`, "g"), params[p]);
      });
      return text;
    }
    return fallback;
  }

  updateLanguage() {
    this.updateSortLabelUi();
    this.updateLibraryStatsUi();
    if (this.elements.playlistModal && !this.elements.playlistModal.classList.contains("hidden")) {
      if (this.activePlaylistId) {
        this.renderPlaylistDetailTracks();
      } else {
        this.renderPlaylistsList();
      }
    }
    if (this.elements.songPickerModal && !this.elements.songPickerModal.classList.contains("hidden")) {
      this.renderSongPicker();
    }
    if (this.isSelectionMode) {
      this.updateSelectionUi();
    }
    if (this.elements.queueModal && !this.elements.queueModal.classList.contains("hidden")) {
      this.renderQueueList();
    }
    this.renderCurrentTab();
  }

  handleBackButton() {
    if (this.elements.deleteConfirmModal && !this.elements.deleteConfirmModal.classList.contains("hidden")) {
      this.closeDeleteConfirmDialog();
      return true;
    }
    if (this.elements.moveTargetModal && !this.elements.moveTargetModal.classList.contains("hidden")) {
      this.closeMoveTargetDialog();
      return true;
    }
    if (this.isSelectionMode) {
      this.exitSelectionMode();
      return true;
    }
    if (this.elements.songPickerModal && !this.elements.songPickerModal.classList.contains("hidden")) {
      this.closeSongPicker();
      return true;
    }
    if (this.elements.createPlaylistModal && !this.elements.createPlaylistModal.classList.contains("hidden")) {
      this.closeCreatePlaylistDialog();
      return true;
    }
    if (this.elements.addToPlaylistModal && !this.elements.addToPlaylistModal.classList.contains("hidden")) {
      this.closeAddToPlaylistModal();
      return true;
    }
    if (this.elements.playlistModal && !this.elements.playlistModal.classList.contains("hidden")) {
      this.handlePlaylistBack();
      return true;
    }
    if (this.elements.fullPlayerModal && !this.elements.fullPlayerModal.classList.contains("hidden")) {
      this.closeFullPlayer();
      return true;
    }
    if (this.elements.queueModal && !this.elements.queueModal.classList.contains("hidden")) {
      this.closeQueueModal();
      return true;
    }
    if (this.elements.sleepTimerModal && !this.elements.sleepTimerModal.classList.contains("hidden")) {
      this.closeSleepTimerModal();
      return true;
    }
    if (this.elements.trackContextMenu && !this.elements.trackContextMenu.classList.contains("hidden")) {
      this.hideTrackContextMenu();
      return true;
    }
    if (this.selectedArtist || this.selectedAlbum) {
      this.closeDrilldown();
      return true;
    }
    return false;
  }
}

// Global initialization
window.addEventListener("DOMContentLoaded", () => {
  window.nimiyoMusicPlayer = new NimiyoMusicPlayer();
});

window.addEventListener("beforeunload", () => {
  if (window.nimiyoMusicPlayer) {
    window.nimiyoMusicPlayer.clearNativeNotification();
  }
});

window.addEventListener("pagehide", () => {
  if (window.nimiyoMusicPlayer) {
    window.nimiyoMusicPlayer.clearNativeNotification();
  }
});
