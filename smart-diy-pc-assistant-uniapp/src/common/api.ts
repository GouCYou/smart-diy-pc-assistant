const rawApiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'https://api-smartdiy.cctstudio.cn/').trim()

export const API_BASE_URL = rawApiBaseUrl.replace(/\/+$/, '')

export function normalizeMediaUrl(url : string) : string {
	if (url.length == 0) {
		return ''
	}
	return url.trim()
}
