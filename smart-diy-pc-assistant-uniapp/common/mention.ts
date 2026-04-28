import { normalizeMediaUrl } from './api'

export type MentionItem = {
	userId : number
	nickname : string
}

export type MentionToken = {
	text : string
	userId : number
}

export type MentionSearchUser = {
	userId : number
	nickname : string
	avatarUrl : string
}

export function normalizeMentions(raw : any) : MentionItem[] {
	if (!Array.isArray(raw)) {
		return [] as MentionItem[]
	}
	return raw
		.map((item : any) : MentionItem => ({
			userId: Number(item == null || item.userId == null ? 0 : item.userId),
			nickname: item == null || item.nickname == null ? '' : item.nickname as string
		}))
		.filter((item : MentionItem) : boolean => item.userId > 0 && item.nickname.length > 0)
}

export function buildMentionTokens(content : string, mentions : MentionItem[]) : MentionToken[] {
	const source = content == null ? '' : content
	if (source.length == 0) {
		return [] as MentionToken[]
	}
	const safeMentions = mentions == null ? [] as MentionItem[] : mentions
	const mentionMap = new Map<string, number>()
	for (let i = 0; i < safeMentions.length; i++) {
		mentionMap.set(safeMentions[i].nickname, safeMentions[i].userId)
	}
	if (mentionMap.size == 0) {
		return [{ text: source, userId: 0 }] as MentionToken[]
	}
	const tokens = [] as MentionToken[]
	const pattern = /@([A-Za-z0-9_\-.\u4e00-\u9fa5]{1,64})/g
	let cursor = 0
	let match = pattern.exec(source)
	while (match != null) {
		if (match.index > cursor) {
			tokens.push({ text: source.substring(cursor, match.index), userId: 0 } as MentionToken)
		}
		const nickname = match[1]
		const userId = mentionMap.get(nickname) || 0
		tokens.push({ text: match[0], userId: userId } as MentionToken)
		cursor = match.index + match[0].length
		match = pattern.exec(source)
	}
	if (cursor < source.length) {
		tokens.push({ text: source.substring(cursor), userId: 0 } as MentionToken)
	}
	return tokens
}

export function getActiveMentionQuery(value : string) : string | null {
	const source = value == null ? '' : value
	const atIndex = source.lastIndexOf('@')
	if (atIndex < 0) {
		return null
	}
	const query = source.substring(atIndex + 1)
	if (query.length > 32 || /[\s@，。！？!?,.;；：:]/.test(query)) {
		return null
	}
	return query
}

export function insertMention(value : string, nickname : string) : string {
	const source = value == null ? '' : value
	const atIndex = source.lastIndexOf('@')
	if (atIndex < 0) {
		return source + '@' + nickname + ' '
	}
	return source.substring(0, atIndex) + '@' + nickname + ' '
}

export function normalizeMentionSearchUsers(raw : any) : MentionSearchUser[] {
	if (!Array.isArray(raw)) {
		return [] as MentionSearchUser[]
	}
	return raw
		.map((item : any) : MentionSearchUser => ({
			userId: Number(item == null || item.userId == null ? 0 : item.userId),
			nickname: item == null || item.nickname == null ? '' : item.nickname as string,
			avatarUrl: normalizeMediaUrl(item == null || item.avatarUrl == null ? '' : item.avatarUrl as string)
		}))
		.filter((item : MentionSearchUser) : boolean => item.userId > 0 && item.nickname.length > 0)
}
