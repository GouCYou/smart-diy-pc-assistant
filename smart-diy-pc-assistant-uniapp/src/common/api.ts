export const API_BASE_URL = 'http://8.138.151.12:8080'

export function normalizeMediaUrl(url : string) : string {
	if (url.length == 0) {
		return ''
	}
	return url.trim()
}
