fn main() -> Result<(), Box<dyn std::error::Error>> {
    let protoc = protoc_bin_vendored::protoc_bin_path()?;
    std::env::set_var("PROTOC", protoc);

    tonic_build::configure()
        .build_server(true)
        .build_client(true)
        .compile_protos(&["../../proto/guard/v1/guard.proto"], &["../../proto"])?;

    println!("cargo:rerun-if-changed=../../proto/guard/v1/guard.proto");

    Ok(())
}
