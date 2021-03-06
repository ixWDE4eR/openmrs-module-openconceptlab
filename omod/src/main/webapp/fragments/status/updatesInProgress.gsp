<script type="text/javascript">
	jq = jQuery;
	jq(function() {
		jq.getJSON('${ui.actionLink("openconceptlab", "status", "getUpdateProgress")}', function(result) {
			jq('#completed').html(result.progress);
			jq('#time').html(result.time);
			jq(function() {
				var progressbar = jq( "#progressbar" );
				progressLabel = jq(".progress-label" );
				jq( "#progressbar" ).progressbar({
					value: false,
					change: function() {
						progressLabel.text(
							progressbar.progressbar( "value" ) + "%" );
					},
					complete: function() {
						progressLabel.text( "Update Completed!" );
					}
				});
				function progress() {
					var val = progressbar.progressbar( "value" ) || 0;
					progressbar.progressbar( "value", val + 1 );
					if ( val < result.progress ) {
						setTimeout( progress, 100 );
					}
				}
				setTimeout( progress, 1000 );
			});
		}, 1000 );
	});
</script>
<style type="text/css">
	.progress-label {
		position: absolute;
		text-align: center;
		font-weight: bold;
		left: 20%;
		text-shadow: 1px 1px 0 #fff;
}
</style>

<fieldset id="updates-progressing">
	<legend id="progress-title">Update in progress</legend>
		<p>
			Upgrade is in progress for <span id="time"></span> seconds now
		</p>
		<p>
				Completed <span id="completed"></span> %
		</p>
		<p>
			<div id="progressbar">
				<div class="progress-label">
					Loading...
				</div
			</div>
		</p>
</fieldset>

