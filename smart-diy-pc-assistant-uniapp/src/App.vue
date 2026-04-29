<script>
export default {
	onLaunch() {
		console.log('App Launch')
	},
	onShow() {
		console.log('App Show')
	},
	onHide() {
		console.log('App Hide')
	}
}
</script>

<style>
	page,
	body,
	uni-page-body {
		min-height: 100%;
		background-color: #f5f7fb;
		color: #111827;
		font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif;
	}

	view,
	uni-view,
	scroll-view,
	uni-scroll-view,
	movable-area,
	uni-movable-area,
	movable-view,
	uni-movable-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	body,
	uni-page-body,
	scroll-view,
	uni-scroll-view,
	.uni-scroll-view,
	.uni-scroll-view-content {
		scrollbar-width: none;
		-ms-overflow-style: none;
	}

	::-webkit-scrollbar {
		width: 0;
		height: 0;
		display: none;
		background: transparent;
	}

	.uni-row {
		flex-direction: row;
	}

	.uni-column {
		flex-direction: column;
	}
</style>
