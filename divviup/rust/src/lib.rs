use std::{ptr, slice};

use janus_core::hpke::{self, is_hpke_config_supported, HpkeApplicationInfo, Label};
use janus_messages::{
    HpkeCiphertext, HpkeConfig, HpkeConfigList, InputShareAad, PlaintextInputShare, Report,
    ReportId, ReportMetadata, Role, TaskId, Time,
};
use jni::{
    objects::{JByteArray, JClass, ReleaseMode},
    sys::{jboolean, jbyteArray, jlong},
    JNIEnv,
};
use prio::{
    codec::{Decode, Encode},
    vdaf::{self, prio3::Prio3},
};
use rand::random;

/// JNI entry point to prepare a Prio3Count report.
///
/// Note that the timestamp argument should already be rounded down according to the DAP task's
/// time_precision.
#[no_mangle]
pub extern "system" fn Java_org_divviup_android_Client_prepareReportPrio3Count<'local>(
    mut env: JNIEnv<'local>,
    _this: JClass<'local>,
    task_id_byte_array: JByteArray<'local>,
    leader_hpke_config_list_byte_array: JByteArray<'local>,
    helper_hpke_config_list_byte_array: JByteArray<'local>,
    timestamp: jlong,
    measurement: jboolean,
) -> jbyteArray {
    let report = match prepare_report_prio3count_inner(
        &task_id_byte_array,
        &leader_hpke_config_list_byte_array,
        &helper_hpke_config_list_byte_array,
        timestamp,
        measurement,
        &mut env,
    ) {
        Ok(report) => report,
        Err(e) => {
            let _ = env.throw(e);
            return ptr::null_mut();
        }
    };

    match return_new_byte_array(&report, &mut env) {
        Ok(array) => array,
        Err(e) => {
            let _ = env.throw(e);
            ptr::null_mut()
        }
    }
}

/// Shard a Prio3Count measurement, and construct a DAP report.
///
/// This is separated from [`Java_org_divviup_android_Client_prepareReportPrio3Count`] to simplify
/// error handling.
fn prepare_report_prio3count_inner<'local, 'a>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    timestamp: jlong,
    measurement: jboolean,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u8>, String> {
    let vdaf = Prio3::new_count(2).map_err(|e| e.to_string())?;
    let measurement = measurement as u64;
    prepare_report_generic(
        task_id_byte_array,
        leader_hpke_config_list_byte_array,
        helper_hpke_config_list_byte_array,
        timestamp,
        vdaf,
        &measurement,
        env,
    )
}

/// Shard a measurement for any VDAF, and construct a DAP report.
///
/// The body of this generic function is kept small to reduce the amount of monomorphized code. Once
/// all work with generic types is complete, [`assemble_report`] completes the rest of it.
fn prepare_report_generic<'local, 'a, V>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    timestamp: jlong,
    vdaf: V,
    measurement: &V::Measurement,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u8>, String>
where
    V: vdaf::Client<16>,
{
    let report_id: ReportId = random();
    let (public_share, input_shares) = vdaf
        .shard(measurement, report_id.as_ref())
        .map_err(|e| e.to_string())?;
    let encoded_leader_input_share = input_shares[0].get_encoded();
    let encoded_helper_input_share = input_shares[1].get_encoded();
    let encoded_public_share = public_share.get_encoded();
    assemble_report(
        task_id_byte_array,
        leader_hpke_config_list_byte_array,
        helper_hpke_config_list_byte_array,
        env,
        timestamp,
        report_id,
        encoded_public_share,
        encoded_leader_input_share,
        encoded_helper_input_share,
    )
}

/// Construct and encode a DAP report from a set of encoded VDAF shares and other inputs.
///
/// This is separated from code in `prepare_report_*` to eliminate common non-generic code from
/// generic methods, reducing the amount of duplicated code appearing in multiple monomorphizations.
#[allow(clippy::too_many_arguments)]
fn assemble_report<'local, 'a>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    env: &mut JNIEnv<'local>,
    timestamp: i64,
    report_id: ReportId,
    encoded_public_share: Vec<u8>,
    encoded_leader_input_share: Vec<u8>,
    encoded_helper_input_share: Vec<u8>,
) -> Result<Vec<u8>, String> {
    // Safety: These byte arrays are not mutated again from the Java side once they are passed in.
    // Only one `AutoElements` is constructed from each, in these calls.
    let task_id = unsafe { parse_task_id(task_id_byte_array, env)? };
    let leader_hpke_config_list =
        unsafe { decode_hpke_config_list(leader_hpke_config_list_byte_array, env)? };
    let helper_hpke_config_list =
        unsafe { decode_hpke_config_list(helper_hpke_config_list_byte_array, env)? };

    let leader_hpke_config = select_hpke_config(&leader_hpke_config_list)?;
    let helper_hpke_config = select_hpke_config(&helper_hpke_config_list)?;

    let time = Time::from_seconds_since_epoch(u64::try_from(timestamp).map_err(|e| e.to_string())?);
    let report_metadata = ReportMetadata::new(report_id, time);

    let leader_encrypted_input_share = encrypt_input_share(
        task_id,
        &report_metadata,
        &Role::Leader,
        &leader_hpke_config,
        encoded_leader_input_share,
        encoded_public_share.clone(),
    )
    .map_err(|e| e.to_string())?;
    let helper_encrypted_input_share = encrypt_input_share(
        task_id,
        &report_metadata,
        &Role::Helper,
        &helper_hpke_config,
        encoded_helper_input_share,
        encoded_public_share.clone(),
    )
    .map_err(|e| e.to_string())?;

    let report = Report::new(
        report_metadata,
        encoded_public_share,
        leader_encrypted_input_share,
        helper_encrypted_input_share,
    );
    Ok(report.get_encoded())
}

/// Read from a Java byte[] array, and interpret the bytes as a [`TaskId`].
///
/// This returns an error if the argument is null, or if the array is not 32 bytes long.
///
/// # Safety
///
/// There must not be any data races on the `byte[]` array, from either Java or Rust.
///
/// This function creates an [`AutoElements`][jni::objects::AutoElements] with the [`JByteArray`],
/// and no other [`AutoElements`][jni::objects::AutoElements] or
/// [`AutoElementsCritical`][jni::objects::AutoElementsCritical] may alias the array.
unsafe fn parse_task_id<'local, 'a>(
    array: &'a JByteArray<'local>,
    env: &'a mut JNIEnv<'local>,
) -> Result<TaskId, String> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller. The [u8]
    // slice aliases a [i8] slice, and the two have the same memory layout. The backing memory is
    // managed by the JVM. The memory is valid for long enough because it is only released when the
    // `AutoElements` struct is dropped, which happens after the last use of the slices.
    let elements = env
        .get_array_elements(array, ReleaseMode::NoCopyBack)
        .map_err(|e| e.to_string())?;
    let signed_slice: &[i8] = &elements[..];
    let bytes: &[u8] =
        slice::from_raw_parts(signed_slice.as_ptr() as *const u8, signed_slice.len());
    TaskId::try_from(bytes).map_err(|e| e.to_string())
}

/// Read from a Java byte[] array, and parse an [`HpkeConfigList`] from it. This returns an error if
/// the argument is null, or if the encoded data is invalid.
///
/// # Safety
///
/// There must not be any data races on the `byte[]` array, from either Java or Rust.
///
/// This function creates an [`AutoElements`][jni::objects::AutoElements] with the [`JByteArray`],
/// and no other [`AutoElements`][jni::objects::AutoElements] or
/// [`AutoElementsCritical`][jni::objects::AutoElementsCritical] may alias the array.
unsafe fn decode_hpke_config_list<'local, 'a>(
    array: &'a JByteArray<'local>,
    env: &'a mut JNIEnv<'local>,
) -> Result<HpkeConfigList, String> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller. The [u8]
    // slice aliases a [i8] slice, and the two have the same memory layout. The backing memory is
    // managed by the JVM. The memory is valid for long enough because it is only released when the
    // `AutoElements` struct is dropped, which happens after the last use of the slices.
    let elements = env
        .get_array_elements(array, ReleaseMode::NoCopyBack)
        .map_err(|e| e.to_string())?;
    let signed_slice: &[i8] = &elements[..];
    let bytes: &[u8] =
        slice::from_raw_parts(signed_slice.as_ptr() as *const u8, signed_slice.len());
    HpkeConfigList::get_decoded(bytes).map_err(|e| e.to_string())
}

/// Copy a byte slice to a Java byte[] array. This returns an error if the argument is null.
///
/// # Safety
///
/// There must not be any data races on the `byte[]` array, from either Java or Rust.
///
/// This function creates an [`AutoElements`][jni::objects::AutoElements] with the [`JByteArray`],
/// and no other [`AutoElements`][jni::objects::AutoElements] or
/// [`AutoElementsCritical`][jni::objects::AutoElementsCritical] may alias the array.
unsafe fn write_byte_array<'local, 'a>(
    array: &'a JByteArray<'local>,
    data: &[u8],
    env: &'a mut JNIEnv<'local>,
) -> Result<(), jni::errors::Error> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller. The [u8]
    // mutable slice points to the same memory as the [i8] slice, and the two have the same memory
    // layout. The two mutable slices are not in use at the same time. The backing memory is managed
    // by the JVM. The memory is valid for long enough because it is only released when the
    // `AutoElements` struct is dropped, which happens after the last use of the slices.
    let mut elements = env.get_array_elements(array, ReleaseMode::CopyBack)?;
    let signed_slice: &mut [i8] = &mut elements[..];
    let len = signed_slice.len();
    let mut_slice: &mut [u8] = slice::from_raw_parts_mut(signed_slice.as_ptr() as *mut u8, len);
    mut_slice.copy_from_slice(data);
    elements.commit()?;
    Ok(())
}

/// Creates a new byte[] array, copies the provided data into it, and returns a raw JNI pointer to
/// the array. This pointer is intended to be returned from a JNI method.
///
/// This returns an error if the byte slice's length cannot fit in an i32, or if the JVM fails to
/// create or update the array.
fn return_new_byte_array(data: &[u8], env: &mut JNIEnv<'_>) -> Result<jbyteArray, String> {
    let length = data
        .len()
        .try_into()
        .map_err(|_| "length overflow".to_string())?;

    let byte_array = env.new_byte_array(length).map_err(|e| e.to_string())?;

    // Safety: there are no races on this array, and it will not be aliased, because it is newly
    // created. The `AutoElements` will release its reference before the array is returned to Java
    // code.
    unsafe { write_byte_array(&byte_array, data, env) }.map_err(|e| e.to_string())?;

    Ok(byte_array.into_raw())
}

/// Select an [`HpkeConfig`] from an [`HpkeConfigList`] that uses a supported set of algorithms.
///
/// Returns an error if the list is empty, or if all sets of algorithms are unsupported.
fn select_hpke_config(list: &HpkeConfigList) -> Result<HpkeConfig, String> {
    if list.hpke_configs().is_empty() {
        return Err("aggregator provided empty HpkeConfigList".to_string());
    }

    // Take the first supported HpkeConfig from the list. Return the first error otherwise.
    let mut first_error = None;
    for config in list.hpke_configs() {
        match is_hpke_config_supported(config) {
            Ok(()) => return Ok(config.clone()),
            Err(e) => {
                if first_error.is_none() {
                    first_error = Some(e.to_string());
                }
            }
        }
    }
    // Unwrap safety: we checked that the list is nonempty, and if we fell through to here, we must
    // have seen at least one error.
    Err(first_error.unwrap())
}

/// Convenience method to construct a [`PlaintextInputShare`], encode it, and encrypt it.
fn encrypt_input_share(
    task_id: TaskId,
    report_metadata: &ReportMetadata,
    receiver_role: &Role,
    hpke_config: &HpkeConfig,
    input_share: Vec<u8>,
    encoded_public_share: Vec<u8>,
) -> Result<HpkeCiphertext, hpke::Error> {
    let plaintext = PlaintextInputShare::new(Vec::new(), input_share).get_encoded();
    hpke::seal(
        hpke_config,
        &HpkeApplicationInfo::new(&Label::InputShare, &Role::Client, receiver_role),
        &plaintext,
        &InputShareAad::new(task_id, report_metadata.clone(), encoded_public_share).get_encoded(),
    )
}
