export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://weiladream.cn:18080'

export function normalizeMediaUrl(url : string) : string {
	if (url.length == 0) {
		return ''
	}
	return url.trim()
}
