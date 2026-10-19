import SwiftUI
import AVFoundation

struct QRScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onError: (String) -> Void
    func makeUIViewController(context: Context) -> ScannerController {
        let controller = ScannerController(); controller.onCode = onCode; controller.onError = onError
        return controller
    }
    func updateUIViewController(_ controller: ScannerController, context: Context) { }
    static func dismantleUIViewController(_ controller: ScannerController, coordinator: ()) { controller.stop() }
}

final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: (String) -> Void = { _ in }
    var onError: (String) -> Void = { _ in }
    private let capture = AVCaptureSession()
    private let queue = DispatchQueue(label: "dev.opencode.camera")
    private var preview: AVCaptureVideoPreviewLayer?
    private var delivered = false
    // Main-thread lifecycle flag also guards delayed camera permission callbacks.
    private var stopped = false
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self, !self.stopped else { return }
                    if granted { self.configure() }
                    else { self.onError("Camera permission denied. Paste the pairing link instead, or enable Camera in Settings.") }
                }
            }
        default: onError("Camera permission denied. Paste the pairing link instead, or enable Camera in Settings.")
        }
    }
    private func configure() {
        guard !stopped else { return }
        let layer = AVCaptureVideoPreviewLayer(session: capture)
        layer.videoGravity = .resizeAspectFill; view.layer.addSublayer(layer); preview = layer
        layer.frame = view.bounds
        queue.async { [weak self] in
            guard let self else { return }
            do {
                guard let camera = AVCaptureDevice.default(for: .video) else { throw MobileError.message("Camera unavailable. Paste the pairing link instead.") }
                let input = try AVCaptureDeviceInput(device: camera)
                let output = AVCaptureMetadataOutput()
                self.capture.beginConfiguration()
                guard self.capture.canAddInput(input), self.capture.canAddOutput(output) else {
                    self.capture.commitConfiguration(); throw MobileError.message("Camera unavailable")
                }
                self.capture.addInput(input); self.capture.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main); output.metadataObjectTypes = [.qr]
                self.capture.commitConfiguration(); self.capture.startRunning()
            } catch { DispatchQueue.main.async { if !self.stopped { self.onError("Camera unavailable. Paste the pairing link instead.") } } }
        }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview?.frame = view.bounds }
    func stop() {
        stopped = true
        queue.async { [capture] in if capture.isRunning { capture.stopRunning() } }
    }
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !delivered, !stopped, let code = metadataObjects.compactMap({ ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue }).first else { return }
        delivered = true; stop(); onCode(code)
    }
}
