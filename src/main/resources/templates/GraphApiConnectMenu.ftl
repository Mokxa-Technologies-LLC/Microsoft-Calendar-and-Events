<#-- Graph API Connect Menu Template -->
<div class="graph-api-connect-menu">
    <#if isPreview?? && isPreview == true>
        <div class="alert alert-info">
            <strong>Preview Mode</strong> - This is how the menu will appear. Sign in functionality is disabled in preview.
        </div>
    </#if>

    <#if data??>
        <#if data.error??>
            <div class="alert alert-danger">
                <strong>Error:</strong> ${data.error}
            </div>
        <#else>
            <#if data.connected?? && data.connected == true>
                <div class="alert alert-success">
                    <i class="fas fa-check-circle"></i> <strong>Connected to Microsoft Graph</strong>
                    <#if data.username??>
                        <br/>Joget User: <strong>${data.username}</strong>
                    </#if>
                </div>
                <#if data.access_token??>
                    <div class="card mt-3">
                        <div class="card-header">
                            <strong>Access Token</strong> (for debugging)
                        </div>
                        <div class="card-body">
                            <pre style="white-space:pre-wrap; word-wrap:break-word; font-size:11px;">${data.access_token}</pre>
                        </div>
                    </div>
                </#if>
            <#else>
                <#-- Not connected, show sign in button -->
                <div class="text-center p-4">
                    <h5>Connect to Microsoft Graph</h5>
                    <p class="text-muted">Sign in with your Microsoft account to connect this application.</p>
                    <#if data.authUrl?? && !(isPreview?? && isPreview == true)>
                        <a class="btn btn-primary btn-lg" href="${data.authUrl}">
                            <i class="fab fa-microsoft"></i> Sign in with Microsoft
                        </a>
                    <#else>
                        <button class="btn btn-primary btn-lg" disabled>
                            <i class="fab fa-microsoft"></i> Sign in with Microsoft
                        </button>
                        <#if isPreview?? && isPreview == true>
                            <p class="text-muted mt-2"><small>Sign in is disabled in preview mode</small></p>
                        </#if>
                    </#if>
                </div>
            </#if>
        </#if>
    <#else>
        <div class="alert alert-warning">
            <strong>Warning:</strong> No data available. Please check plugin configuration.
        </div>
    </#if>
</div>

<style>
.graph-api-connect-menu {
padding: 20px;
}
.graph-api-connect-menu .card {
border: 1px solid #ddd;
border-radius: 4px;
}
.graph-api-connect-menu .card-header {
background-color: #f5f5f5;
padding: 10px 15px;
border-bottom: 1px solid #ddd;
}
.graph-api-connect-menu .card-body {
padding: 15px;
max-height: 300px;
overflow-y: auto;
}
</style>