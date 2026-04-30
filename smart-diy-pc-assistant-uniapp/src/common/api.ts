export const API_BASE_URL = 'http://localhost:8080'

export function normalizeMediaUrl(url : string) : string {
	if (url.length == 0) {
		return ''
	}
	return url.trim()
}
