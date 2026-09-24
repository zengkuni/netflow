# Add project specific ProGuard rules here.
# Keep app entry points
-keep class netflow.dev.MainActivity { *; }
-keep class netflow.dev.service.ProxyService { *; }
-keep class netflow.dev.NetflowApplication { *; }

# Keep coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
