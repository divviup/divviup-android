#![deny(unsafe_op_in_unsafe_fn)]

use std::{ptr, slice};

use janus_core::hpke::{self, is_hpke_config_supported, HpkeApplicationInfo, Label};
use janus_messages::{
    HpkeCiphertext, HpkeConfig, HpkeConfigList, InputShareAad, PlaintextInputShare, Report,
    ReportId, ReportMetadata, Role, TaskId, Time,
};
use jni::{
    objects::{JByteArray, JClass, JLongArray, ReleaseMode},
    sys::{jboolean, jbyteArray, jlong, jobject},
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
pub extern "system" fn Java_org_divviup_android_Client_00024Prio3CountReportPreparer_prepareReportNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _this: JClass<'local>,
    task_id_byte_array: JByteArray<'local>,
    leader_hpke_config_list_byte_array: JByteArray<'local>,
    helper_hpke_config_list_byte_array: JByteArray<'local>,
    timestamp: jlong,
    measurement: jboolean,
) -> jbyteArray {
    jni_try(&mut env, |env: &mut JNIEnv<'_>| {
        let report = prepare_report_prio3count_inner(
            &task_id_byte_array,
            &leader_hpke_config_list_byte_array,
            &helper_hpke_config_list_byte_array,
            timestamp,
            measurement,
            env,
        )?;
        return_new_byte_array(&report, env)
    })
}

/// JNI entry point to prepare a Prio3Sum report.
///
/// Note that the timestamp argument should already be rounded down according to the DAP task's
/// time_precision.
#[no_mangle]
pub extern "system" fn Java_org_divviup_android_Client_00024Prio3SumReportPreparer_prepareReportNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _this: JClass<'local>,
    task_id_byte_array: JByteArray<'local>,
    leader_hpke_config_list_byte_array: JByteArray<'local>,
    helper_hpke_config_list_byte_array: JByteArray<'local>,
    timestamp: jlong,
    bits: jlong,
    measurement: jlong,
) -> jbyteArray {
    jni_try(&mut env, |env: &mut JNIEnv<'_>| {
        let report = prepare_report_prio3sum_inner(
            &task_id_byte_array,
            &leader_hpke_config_list_byte_array,
            &helper_hpke_config_list_byte_array,
            timestamp,
            bits,
            measurement,
            env,
        )?;
        return_new_byte_array(&report, env)
    })
}

/// JNI entry point to prepare a Prio3SumVec report.
///
/// Note that the timestamp argument should already be rounded down according to the DAP task's
/// time_precision.
#[no_mangle]
pub extern "system" fn Java_org_divviup_android_Client_00024Prio3SumVecReportPreparer_prepareReportNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _this: JClass<'local>,
    task_id_byte_array: JByteArray<'local>,
    leader_hpke_config_list_byte_array: JByteArray<'local>,
    helper_hpke_config_list_byte_array: JByteArray<'local>,
    timestamp: jlong,
    length: jlong,
    bits: jlong,
    chunk_length: jlong,
    measurement: JLongArray<'local>,
) -> jbyteArray {
    jni_try(&mut env, |env: &mut JNIEnv<'_>| {
        let report = prepare_report_prio3sumvec_inner(
            &task_id_byte_array,
            &leader_hpke_config_list_byte_array,
            &helper_hpke_config_list_byte_array,
            timestamp,
            length,
            bits,
            chunk_length,
            &measurement,
            env,
        )?;
        return_new_byte_array(&report, env)
    })
}

/// JNI entry point to prepare a Prio3Histogram report.
///
/// Note that the timestamp argument should already be rounded down according to the DAP task's
/// time_precision.
#[no_mangle]
pub extern "system" fn Java_org_divviup_android_Client_00024Prio3HistogramReportPreparer_prepareReportNative<
    'local,
>(
    mut env: JNIEnv<'local>,
    _this: JClass<'local>,
    task_id_byte_array: JByteArray<'local>,
    leader_hpke_config_list_byte_array: JByteArray<'local>,
    helper_hpke_config_list_byte_array: JByteArray<'local>,
    timestamp: jlong,
    length: jlong,
    chunk_length: jlong,
    measurement: jlong,
) -> jbyteArray {
    jni_try(&mut env, |env: &mut JNIEnv<'_>| {
        let report = prepare_report_prio3histogram_inner(
            &task_id_byte_array,
            &leader_hpke_config_list_byte_array,
            &helper_hpke_config_list_byte_array,
            timestamp,
            length,
            chunk_length,
            measurement,
            env,
        )?;
        return_new_byte_array(&report, env)
    })
}

#[derive(Debug, thiserror::Error)]
enum Error {
    #[error("message encoding failed: {0}")]
    Codec(#[from] prio::codec::CodecError),
    #[error("{0}")]
    Hpke(#[from] janus_core::hpke::Error),
    #[error("unexpected value for jboolean")]
    JBoolean,
    #[error("JNI error: {0}")]
    Jni(#[from] jni::errors::Error),
    #[error("message decoding failed: {0}")]
    Message(#[from] janus_messages::Error),
    #[error("aggregator provided empty HPKE config list")]
    MissingHpkeConfigs,
    #[error("integer conversion error: {0}")]
    TryFromInt(#[from] std::num::TryFromIntError),
    #[error("VDAF error: {0}")]
    Vdaf(#[from] prio::vdaf::VdafError),
}

/// Runs a fallible closure that returns a jobject, and transforms an error result into a thrown
/// exception, with a message provided from the error.
fn jni_try<'local, F>(env: &mut JNIEnv<'local>, mut f: F) -> jobject
where
    F: FnMut(&mut JNIEnv<'local>) -> Result<jobject, Error>,
{
    let result = f(env);
    match result {
        Ok(object) => object,
        Err(error) => {
            let _ = env.throw(error.to_string());
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
) -> Result<Vec<u8>, Error> {
    let vdaf = Prio3::new_count(2)?;
    let measurement = match measurement {
        0 => false,
        1 => true,
        _ => return Err(Error::JBoolean),
    };
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

/// Shard a Prio3Sum measurement, and construct a DAP report.
///
/// This is separated from [`Java_org_divviup_android_Client_prepareReportPrio3Sum`] to simplify
/// error handling.
fn prepare_report_prio3sum_inner<'local, 'a>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    timestamp: jlong,
    bits: jlong,
    measurement: jlong,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u8>, Error> {
    let vdaf = Prio3::new_sum(2, bits.try_into()?)?;
    prepare_report_generic(
        task_id_byte_array,
        leader_hpke_config_list_byte_array,
        helper_hpke_config_list_byte_array,
        timestamp,
        vdaf,
        &measurement.try_into()?,
        env,
    )
}

/// Shard a Prio3SumVec measurement, and construct a DAP report.
///
/// This is separated from [`Java_org_divviup_android_Client_prepareReportPrio3SumVec`] to simplify
/// error handling.
#[allow(clippy::too_many_arguments)]
fn prepare_report_prio3sumvec_inner<'local, 'a>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    timestamp: jlong,
    length: jlong,
    bits: jlong,
    chunk_length: jlong,
    measurement: &'a JLongArray<'local>,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u8>, Error> {
    let vdaf = Prio3::new_sum_vec(
        2,
        bits.try_into()?,
        length.try_into()?,
        chunk_length.try_into()?,
    )?;

    // Safety: The copy of the measurement array is not mutated again from the Java side once it is
    // passed in. Only one `AutoElements` is constructed from it, in this call.
    let measurement = unsafe { convert_sumvec_measurement(measurement, env)? };

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

/// Shard a Prio3Histogram measurement, and construct a DAP report.
///
/// This is separated from [`Java_org_divviup_android_Client_prepareReportPrio3Histogram`] to
/// simplify error handling.
#[allow(clippy::too_many_arguments)]
fn prepare_report_prio3histogram_inner<'local, 'a>(
    task_id_byte_array: &'a JByteArray<'local>,
    leader_hpke_config_list_byte_array: &'a JByteArray<'local>,
    helper_hpke_config_list_byte_array: &'a JByteArray<'local>,
    timestamp: jlong,
    length: jlong,
    chunk_length: jlong,
    measurement: jlong,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u8>, Error> {
    let length = length.try_into()?;
    let chunk_length = chunk_length.try_into()?;
    let vdaf = Prio3::new_histogram(2, length, chunk_length)?;
    prepare_report_generic(
        task_id_byte_array,
        leader_hpke_config_list_byte_array,
        helper_hpke_config_list_byte_array,
        timestamp,
        vdaf,
        &measurement.try_into()?,
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
) -> Result<Vec<u8>, Error>
where
    V: vdaf::Client<16>,
{
    let report_id: ReportId = random();
    let (public_share, input_shares) = vdaf.shard(measurement, report_id.as_ref())?;
    let encoded_leader_input_share = input_shares[0].get_encoded()?;
    let encoded_helper_input_share = input_shares[1].get_encoded()?;
    let encoded_public_share = public_share.get_encoded()?;
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
) -> Result<Vec<u8>, Error> {
    // Safety: These byte arrays are not mutated again from the Java side once they are passed in.
    // Only one `AutoElements` is constructed from each, in these calls.
    let task_id = unsafe { parse_task_id(task_id_byte_array, env)? };
    let leader_hpke_config_list =
        unsafe { decode_hpke_config_list(leader_hpke_config_list_byte_array, env)? };
    let helper_hpke_config_list =
        unsafe { decode_hpke_config_list(helper_hpke_config_list_byte_array, env)? };

    let leader_hpke_config = select_hpke_config(&leader_hpke_config_list)?;
    let helper_hpke_config = select_hpke_config(&helper_hpke_config_list)?;

    let time = Time::from_seconds_since_epoch(u64::try_from(timestamp)?);
    let report_metadata = ReportMetadata::new(report_id, time);

    let leader_encrypted_input_share = encrypt_input_share(
        task_id,
        &report_metadata,
        &Role::Leader,
        &leader_hpke_config,
        encoded_leader_input_share,
        encoded_public_share.clone(),
    )?;
    let helper_encrypted_input_share = encrypt_input_share(
        task_id,
        &report_metadata,
        &Role::Helper,
        &helper_hpke_config,
        encoded_helper_input_share,
        encoded_public_share.clone(),
    )?;

    let report = Report::new(
        report_metadata,
        encoded_public_share,
        leader_encrypted_input_share,
        helper_encrypted_input_share,
    );
    Ok(report.get_encoded()?)
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
) -> Result<TaskId, Error> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller.
    let elements = unsafe { env.get_array_elements(array, ReleaseMode::NoCopyBack) }?;
    let signed_slice: &[i8] = &elements[..];
    // Safety: The [u8] slice aliases a [i8] slice, and the two have the same memory layout. The
    // backing memory is managed by the JVM. The memory is valid for long enough because it is only
    // released when the `AutoElements` struct is dropped, which happens after the last use of the
    // slices.
    let bytes: &[u8] =
        unsafe { slice::from_raw_parts(signed_slice.as_ptr() as *const u8, signed_slice.len()) };
    Ok(TaskId::try_from(bytes)?)
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
) -> Result<HpkeConfigList, Error> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller.
    let elements = unsafe { env.get_array_elements(array, ReleaseMode::NoCopyBack) }?;
    let signed_slice: &[i8] = &elements[..];
    // Safety: The [u8] slice aliases a [i8] slice, and the two have the same memory layout. The
    // backing memory is managed by the JVM. The memory is valid for long enough because it is only
    // released when the `AutoElements` struct is dropped, which happens after the last use of the
    // slices.
    let bytes: &[u8] =
        unsafe { slice::from_raw_parts(signed_slice.as_ptr() as *const u8, signed_slice.len()) };
    Ok(HpkeConfigList::get_decoded(bytes)?)
}

/// Read from a Java long[] array, and convert each element to a `u128`. This returns an error if
/// the argument is null, or if any element is negative.
///
/// # Safety
///
/// There must not be any data races on the `byte[]` array, from either Java or Rust.
///
/// This function creates an [`AutoElements`][jni::objects::AutoElements] with the [`JByteArray`],
/// and no other [`AutoElements`][jni::objects::AutoElements] or
/// [`AutoElementsCritical`][jni::objects::AutoElementsCritical] may alias the array.
unsafe fn convert_sumvec_measurement<'local, 'a>(
    array: &'a JLongArray<'local>,
    env: &'a mut JNIEnv<'local>,
) -> Result<Vec<u128>, Error> {
    // Safety: All safety requirements of get_array_elements() are imposed on the caller.
    let elements = unsafe { env.get_array_elements(array, ReleaseMode::NoCopyBack) }?;
    Ok(elements
        .iter()
        .map(|value| u128::try_from(*value))
        .collect::<Result<Vec<_>, _>>()?)
}

/// Creates a new byte[] array, copies the provided data into it, and returns a raw JNI pointer to
/// the array. This pointer is intended to be returned from a JNI method.
///
/// This returns an error if the byte slice's length cannot fit in an i32, or if the JVM fails to
/// create or update the array.
fn return_new_byte_array(data: &[u8], env: &mut JNIEnv<'_>) -> Result<jbyteArray, Error> {
    let length = data.len().try_into()?;

    let byte_array = env.new_byte_array(length)?;

    // Start a new scope for the AutoElements. We need to drop it before calling into_raw() on the
    // byte array, as it borrows the byte array.
    {
        // Safety: There are no races on this array, and it will not be aliased, because it is newly
        // created. The `AutoElements` will release its reference before the array is returned to
        // Java code.
        let mut elements = unsafe { env.get_array_elements(&byte_array, ReleaseMode::CopyBack) }?;
        let signed_slice: &mut [i8] = &mut elements[..];
        let len = signed_slice.len();
        // Safety: The [u8] mutable slice points to the same memory as the [i8] slice, and the two
        // have the same memory layout. The two mutable slices are not in use at the same time. The
        // backing memory is managed by the JVM. The memory is valid for long enough because it is
        // only released when the `AutoElements` struct is dropped, which happens after the last use
        // of the slices.
        let mut_slice: &mut [u8] =
            unsafe { slice::from_raw_parts_mut(signed_slice.as_ptr() as *mut u8, len) };
        mut_slice.copy_from_slice(data);
        elements.commit()?;
    }

    Ok(byte_array.into_raw())
}

/// Select an [`HpkeConfig`] from an [`HpkeConfigList`] that uses a supported set of algorithms.
///
/// Returns an error if the list is empty, or if all sets of algorithms are unsupported.
fn select_hpke_config(list: &HpkeConfigList) -> Result<HpkeConfig, Error> {
    if list.hpke_configs().is_empty() {
        return Err(Error::MissingHpkeConfigs);
    }

    // Take the first supported HpkeConfig from the list. Return the first error otherwise.
    let mut first_error = None;
    for config in list.hpke_configs() {
        match is_hpke_config_supported(config) {
            Ok(()) => return Ok(config.clone()),
            Err(e) => {
                if first_error.is_none() {
                    first_error = Some(e);
                }
            }
        }
    }
    // Unwrap safety: we checked that the list is nonempty, and if we fell through to here, we must
    // have seen at least one error.
    Err(first_error.unwrap().into())
}

/// Convenience method to construct a [`PlaintextInputShare`], encode it, and encrypt it.
fn encrypt_input_share(
    task_id: TaskId,
    report_metadata: &ReportMetadata,
    receiver_role: &Role,
    hpke_config: &HpkeConfig,
    input_share: Vec<u8>,
    encoded_public_share: Vec<u8>,
) -> Result<HpkeCiphertext, Error> {
    let plaintext = PlaintextInputShare::new(Vec::new(), input_share).get_encoded()?;
    Ok(hpke::seal(
        hpke_config,
        &HpkeApplicationInfo::new(&Label::InputShare, &Role::Client, receiver_role),
        &plaintext,
        &InputShareAad::new(task_id, report_metadata.clone(), encoded_public_share)
            .get_encoded()?,
    )?)
}
