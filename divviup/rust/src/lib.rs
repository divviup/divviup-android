use jni::{objects::JClass, sys::jstring, JNIEnv};

#[no_mangle]
pub extern "system" fn Java_org_divviup_android_NativeLib_stringFromJNI<'local>(
    env: JNIEnv<'local>,
    _this: JClass<'local>,
) -> jstring {
    let output = env.new_string("Hello from Rust").unwrap();
    output.into_raw()
}
