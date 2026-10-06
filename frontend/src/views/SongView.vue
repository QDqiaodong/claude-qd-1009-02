<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const languages = ['国语', '粤语', '英文', '日文']
const ALL_LANG = ''
const FILTER_KEY = 'songLangFilter'

// 只接受合法语种或明确的「全部」状态（空串）；持久化值若被改成脏值，收敛为全部，
// 绝不把空白/非法语种发给接口（后端同样会以 400 拒绝，前后端口径一致）。
function normalizeLanguage(v) {
  return languages.includes(v) ? v : ALL_LANG
}

const langFilter = ref(normalizeLanguage(localStorage.getItem(FILTER_KEY)))
function persistLanguage(v) {
  if (v) localStorage.setItem(FILTER_KEY, v)
  else localStorage.removeItem(FILTER_KEY)
}

const dialog = ref(false)
const saving = ref(false)

const form = ref(blank())
function blank() {
  return { songCode: '', songName: '', singer: '', language: '国语', durationSec: '' }
}

// 请求序号：连续切换语种时，只采纳最后一次请求的结果；
// 慢返回的旧请求（无论成功还是失败）一律丢弃，不能覆盖最新筛选对应的状态。
let reqSeq = 0

async function load() {
  const language = normalizeLanguage(langFilter.value)
  if (language !== langFilter.value) langFilter.value = language
  persistLanguage(language)

  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    // 全部状态：不带 language 参数（后端口径：缺省=全库；空白/非法=400）
    const data = await api.listSongs(language ? { language } : {})
    if (seq !== reqSeq) return // 更新的筛选请求已发出，旧结果不写表
    rows.value = data || []
  } catch (e) {
    if (seq !== reqSeq) return // 旧请求的失败同样不能覆盖最新状态
    rows.value = [] // 当前请求失败：清掉过期列表，避免表格与筛选框不一致
    error.value = e.message
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

const emptyText = computed(() => {
  if (error.value) return '列表加载失败，请查看上方错误提示'
  return langFilter.value ? `暂无${langFilter.value}歌曲，可切换语种或选择「全部语种」` : '歌曲库暂无歌曲'
})

function fmtDur(s) {
  if (s == null) return '—'
  const m = Math.floor(s / 60)
  const ss = String(s % 60).padStart(2, '0')
  return `${m}:${ss}`
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, durationSec: form.value.durationSec ? Number(form.value.durationSec) : null }
    await api.createSong(d)
    tip.value = `歌曲 ${d.songName} 已入库`
    dialog.value = false
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">歌曲库 · 语种与时长</span>
        <span class="panel-desc">编码唯一，语种须为 国语/粤语/英文/日文</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="toolbar">
        <el-select
          v-model="langFilter"
          placeholder="全部语种"
          clearable
          style="width: 140px"
          @change="load"
        >
          <el-option label="全部语种" :value="ALL_LANG" />
          <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">入库歌曲</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" :empty-text="emptyText" border stripe>
        <el-table-column prop="songCode" label="编码" width="90" />
        <el-table-column prop="songName" label="歌名" min-width="140" />
        <el-table-column prop="singer" label="歌手" width="130" />
        <el-table-column prop="language" label="语种" width="90">
          <template #default="{ row }"><el-tag>{{ row.language }}</el-tag></template>
        </el-table-column>
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ fmtDur(row.durationSec) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="入库歌曲" width="560px">
      <el-form :model="form" label-width="90px" class="dialog-form">
        <el-form-item label="编码"><el-input v-model="form.songCode" placeholder="如 S006" /></el-form-item>
        <el-form-item label="歌名"><el-input v-model="form.songName" /></el-form-item>
        <el-form-item label="歌手"><el-input v-model="form.singer" /></el-form-item>
        <el-form-item label="语种">
          <el-select v-model="form.language">
            <el-option v-for="l in languages" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(秒)"><el-input v-model="form.durationSec" type="number" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>
