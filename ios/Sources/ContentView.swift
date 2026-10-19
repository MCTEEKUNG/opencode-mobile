import SwiftUI

struct ContentView: View {
    @Bindable var model: MobileModel
    @State private var pairing = false
    @State private var creating = false
    @State private var directory = ""
    @State private var tab = 0
    @State private var forgetting = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label(model.status, systemImage: model.status == "Connected" ? "circle.fill" : "circle")
                        .font(.caption).foregroundStyle(.mint)
                    Spacer()
                    if model.busy { ProgressView() }
                }.padding(.horizontal)
                if let session = model.selected { conversation(session) }
                else if model.computer != nil { sessions }
                else { welcome }
            }
            .navigationTitle(model.selected?.title ?? "OpenCode")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if model.selected != nil { Button("Sessions", systemImage: "chevron.left") { model.select(nil) } }
                    else {
                        Menu {
                            ForEach(model.computers) { computer in Button(computer.name) { model.connect(computer) } }
                            Button("Add computer", systemImage: "qrcode.viewfinder") { pairing = true }
                            if model.computer != nil { Button("Forget computer", role: .destructive) { forgetting = true } }
                        } label: { Label(model.computer?.name ?? "Computers", systemImage: "desktopcomputer") }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if model.computer != nil { Button("Refresh", systemImage: "arrow.clockwise") { model.refresh() }.disabled(model.busy) }
                }
            }
            .sheet(isPresented: $pairing) { PairingView(model: model) }
            .alert("New session", isPresented: $creating) {
                TextField("Absolute directory on PC", text: $directory).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Create") { model.create(directory: directory) }.disabled(directory.isEmpty)
                Button("Cancel", role: .cancel) { }
            } message: { Text("Enter a directory on the computer, not on this phone.") }
            .alert("Forget this computer?", isPresented: $forgetting) {
                Button("Forget", role: .destructive) { if let computer = model.computer { model.forget(computer) } }
                Button("Cancel", role: .cancel) { }
            } message: { Text("This removes the local credential only. It does not revoke the session on your PC.") }
            .alert("OpenCode", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
                Button("OK") { model.error = nil }
            } message: { Text(model.error ?? "") }
        }
    }
    private var welcome: some View {
        VStack(alignment: .leading, spacing: 20) {
            Image(systemName: "desktopcomputer.and.iphone").font(.system(size: 52)).foregroundStyle(.mint)
            Text("Your PC, in your pocket.").font(.largeTitle.bold())
            Text("Run this on your computer:")
            Text("opencode pair --remote").font(.system(.body, design: .monospaced)).textSelection(.enabled)
            Text("Scan its QR code or paste the HTTPS pairing link. Your computer must stay awake and online.").foregroundStyle(.secondary)
            Button("Connect computer", systemImage: "qrcode.viewfinder") { pairing = true }.buttonStyle(.borderedProminent)
            Spacer()
        }.padding(24)
    }
    private var sessions: some View {
        List {
            Button("New session", systemImage: "plus") { creating = true }.disabled(model.busy)
            ForEach(model.sessions) { session in
                Button { model.select(session) } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(session.title ?? "Untitled session").foregroundStyle(.primary)
                        Text(session.location.directory ?? "").font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 6)
                }
            }
            if model.nextSessions != nil { Button("Load more sessions") { model.moreSessions() }.disabled(model.busy) }
        }.refreshable { model.refresh() }
    }
    private func conversation(_ session: SessionInfo) -> some View {
        VStack(spacing: 8) {
            Picker("Section", selection: $tab) {
                Text("Chat").tag(0)
                Text("Approvals (\(model.permissions.count))").tag(1)
                Text("Changes").tag(2)
            }.pickerStyle(.segmented).padding(.horizontal)
                .onChange(of: tab) { _, value in if value == 2 { model.loadDiff() } }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    if tab == 0 {
                        if model.nextMessages != nil { Button("Load older messages") { model.moreMessages() }.disabled(model.busy) }
                        ForEach(model.messages) { message in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(message.type.uppercased()).font(.caption2.bold()).foregroundStyle(.mint)
                                Text(message.displayText).font(.system(.body, design: .monospaced)).textSelection(.enabled)
                            }.frame(maxWidth: .infinity, alignment: .leading).padding(12)
                                .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
                        }
                    } else if tab == 1 {
                        if model.permissions.isEmpty { Text("No pending approvals").foregroundStyle(.secondary) }
                        ForEach(model.permissions) { request in
                            VStack(alignment: .leading, spacing: 12) {
                                Text(request.action).font(.headline)
                                Text(request.detail).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                                HStack {
                                    Button("Reject", role: .destructive) { model.reply(request, decision: "reject") }
                                    Spacer()
                                    Button("Allow once") { model.reply(request, decision: "once") }.buttonStyle(.borderedProminent)
                                }.disabled(model.busy)
                            }.padding().background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
                        }
                    } else {
                        Button("Refresh changes") { model.loadDiff() }.disabled(model.busy)
                        if model.diffs.isEmpty { Text("No changes loaded").foregroundStyle(.secondary) }
                        ForEach(model.diffs) { diff in
                            Text(diff.file).font(.headline)
                            Text("+\(diff.additions) −\(diff.deletions)").foregroundStyle(.mint).font(.caption)
                            Text(diff.patch).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                        }
                    }
                }.padding()
            }.defaultScrollAnchor(.bottom)
            HStack(alignment: .bottom) {
                TextField("Ask OpenCode…", text: Binding(get: { model.drafts[session.id] ?? "" }, set: { model.drafts[session.id] = $0 }), axis: .vertical)
                    .lineLimit(1...5).textFieldStyle(.roundedBorder)
                Button("Send", systemImage: "arrow.up.circle.fill") { model.send() }
                    .labelStyle(.iconOnly).font(.title).disabled(model.busy || (model.drafts[session.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("Stop", systemImage: "stop.circle") { model.interrupt() }
                    .labelStyle(.iconOnly).font(.title).disabled(model.busy)
            }.padding(.horizontal).padding(.bottom, 8)
        }
    }
}

struct PairingView: View {
    @Bindable var model: MobileModel
    @Environment(\.dismiss) private var dismiss
    @State private var link = ""
    @State private var name = ""
    @State private var scanner = false
    @State private var target: PairingLink?
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("On your computer") { Text("opencode pair --remote").font(.system(.body, design: .monospaced)) }
                Section("Connection") {
                    TextField("Computer name", text: $name)
                    TextField("HTTPS pairing link", text: $link).textInputAutocapitalization(.never).autocorrectionDisabled().privacySensitive()
                    Button("Scan QR code", systemImage: "qrcode.viewfinder") { scanner = true }
                }
                Section {
                    Button("Continue") {
                        do { target = try PairingLink(link) } catch { self.error = error.localizedDescription }
                    }.disabled(link.isEmpty || model.busy)
                } footer: { Text("Pairing links are single-use. Only scan a QR code from your own OpenCode service.") }
            }
            .navigationTitle("Connect computer")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { link = ""; dismiss() } } }
            .sheet(isPresented: $scanner) {
                QRScanner(onCode: { link = $0; scanner = false }, onError: { error = $0; scanner = false }).ignoresSafeArea()
                    .overlay(alignment: .topTrailing) { Button("Cancel") { scanner = false }.padding(24).buttonStyle(.borderedProminent) }
            }
            .alert("Trust this server?", isPresented: Binding(get: { target != nil }, set: { if !$0 { target = nil } })) {
                Button("Connect") {
                    if let target { model.pair(target, name: name) }
                    target = nil; link = ""; dismiss()
                }
                Button("Cancel", role: .cancel) { target = nil }
            } message: { Text("\(target?.origin ?? "")\n\nOnly connect to a computer you trust. This app can run OpenCode actions on it.") }
            .alert("Pairing link", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }
}
