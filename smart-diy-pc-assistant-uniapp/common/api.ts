export const API_BASE_URL = 'https://6.cctstudio.cn'

export function normalizeMediaUrl(url : string) : string {
	if (url.length == 0) {
		return ''
	}
	return url.trim()
}
