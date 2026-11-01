import SwiftUI

/// Sign in with a 6-digit code sent by email. The server decides everything
/// security-related (throttling, attempts, expiry); this screen only explains it.
struct LoginScreen: View {
    @EnvironmentObject private var session: Session
    @Environment(\.openURL) private var openURL
    @State private var email = ""
    @State private var sentTo: String?
    @State private var code = ""
    @State private var devCode: String?
    @State private var resendIn = 0
    @State private var loading = false
    @State private var error: String?
    @FocusState private var codeFocused: Bool

    private var canSend: Bool { !loading && email.contains("@") && email.contains(".") }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(Color.pkPrimary.opacity(0.12)).frame(width: 112, height: 112)
                    LogoMark(size: 76)
                }
                Text(verbatim: "ParkIt").font(PKFont.wordmark(32)).foregroundStyle(Color.pkText).padding(.top, 14)
                Text(L("login_tagline")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center)

                VStack(alignment: .leading, spacing: 0) {
                    if let target = sentTo { codeStep(target) } else { emailStep }
                    if let error {
                        Text(error).font(PKFont.bodySmall).foregroundStyle(Color.pkError).padding(.top, 10)
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.pkSurface, in: RoundedRectangle(cornerRadius: 20))
                .shadow(color: .black.opacity(0.1), radius: 6, y: 2)
                .padding(.top, 28)

                Text(L("login_terms_note")).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 14)
                Text(L("login_agree")).font(PKFont.labelSmall).foregroundStyle(Color.pkMuted).multilineTextAlignment(.center).padding(.top, 8)
                HStack(spacing: 16) {
                    Button(L("legal_terms")) { openURL(LegalLinks.terms) }
                    Button(L("legal_privacy")) { openURL(LegalLinks.privacy) }
                }
                .font(PKFont.labelMedium).foregroundStyle(Color.pkPrimary).padding(.top, 8)
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .defaultScrollAnchor(.center)
        .background(Color.pkBackground.ignoresSafeArea())
        .task(id: resendIn) {
            guard resendIn > 0 else { return }
            try? await Task.sleep(for: .seconds(1))
            if !Task.isCancelled { resendIn -= 1 }
        }
    }

    private var emailStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L("login_email_body")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
            LabeledField(label: L("details_email"), text: $email, placeholder: "name@example.com", keyboard: .emailAddress, capitalization: .never, content: .emailAddress, identifier: "login_email") {
                if canSend { send() }
            }
            .environment(\.layoutDirection, .leftToRight) // an address always reads left to right
            .onChange(of: email) { _, v in if v.count > 254 { email = String(v.prefix(254)) } }
            Button(action: send) {
                if loading { ProgressView().tint(Color.pkOnPrimary) } else { Text(L("login_send_code")) }
            }
            .buttonStyle(PillButtonStyle())
            .disabled(!canSend)
        }
    }

    private func codeStep(_ target: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("login_code_title")).font(PKFont.titleMedium).foregroundStyle(Color.pkText)
            Text(L("login_code_body", "\u{2066}\(target)\u{2069}")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                .padding(.top, 2).padding(.bottom, 14)
            TextField("", text: $code)
                .font(PKFont.heebo(26, 500)).kerning(10)
                .multilineTextAlignment(.center)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($codeFocused)
                .frame(height: 56)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.pkMuted.opacity(0.6), lineWidth: 1))
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityIdentifier("login_code")
                .onChange(of: code) { _, v in
                    let digits = String(v.filter(\.isNumber).prefix(6))
                    if digits != v { code = digits }
                    if digits.count == 6, !loading { verify() } // no extra tap needed
                }
            // A local dev server hands the code back, shown like the message that would have arrived.
            if let devCode {
                Button {
                    code = devCode
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("login_dev_code_title")).font(PKFont.labelMedium).foregroundStyle(Color.pkPrimary)
                        Text(L("login_dev_code", devCode)).font(PKFont.bodyMedium).foregroundStyle(Color.pkText)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.pkPrimary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
                }
                .padding(.top, 10)
            }
            Button(action: verify) {
                if loading { ProgressView().tint(Color.pkOnPrimary) } else { Text(L("login_verify")) }
            }
            .buttonStyle(PillButtonStyle())
            .disabled(loading || code.count != 6)
            .padding(.top, 14)
            HStack {
                Button(L("login_change_email")) {
                    sentTo = nil
                    error = nil
                }
                Spacer()
                Button(resendIn > 0 ? L("login_resend_in", resendIn) : L("login_resend"), action: send)
                    .disabled(resendIn > 0 || loading)
            }
            .font(PKFont.labelLarge).foregroundStyle(Color.pkPrimary).padding(.top, 12)
        }
        .onAppear { codeFocused = true }
    }

    private func explain(_ e: Error) -> String {
        let kind = ParkItError(e)
        RecentErrors.record(kind.code)
        let text: String
        if let api = e as? APIError {
            switch api.status {
            case 422: text = L("details_email_invalid")
            case 503: text = L("login_send_failed")
            case 429: text = L("login_too_many")
            case 400: text = L(api.detail?.contains("expired") == true ? "login_code_expired" : "login_code_wrong")
            default: text = L(kind.messageKey)
            }
        } else {
            text = L(kind.messageKey)
        }
        return L("error_with_code", text, kind.code)
    }

    private func send() {
        loading = true
        error = nil
        Task {
            defer { loading = false }
            do {
                let sent = try await API.shared.requestEmailOtp(email.trimmingCharacters(in: .whitespaces))
                sentTo = sent.email
                devCode = sent.devCode
                resendIn = sent.resendAfterSeconds ?? 60
                code = ""
            } catch {
                self.error = explain(error)
            }
        }
    }

    private func verify() {
        guard let target = sentTo else { return }
        loading = true
        error = nil
        Task {
            defer { loading = false }
            do {
                let token = try await API.shared.verifyEmailOtp(target, code)
                session.save(token: token.accessToken, userId: token.userId, displayName: "")
                session.loggedIn(needsProfile: token.needsProfile == true)
            } catch {
                self.error = explain(error)
                code = ""
            }
        }
    }
}

/// Basic details: asked once after the first sign-in, editable later. Only "first
/// name + last initial" is ever shown to other drivers.
struct DetailsScreen: View {
    let firstTime: Bool
    let onDone: () -> Void
    @EnvironmentObject private var session: Session
    @State private var first = ""
    @State private var last = ""
    @State private var email = ""
    @State private var avatar: String? = avatarId(Int.random(in: 0..<16))
    @State private var saving = false
    @State private var error: String?
    @State private var emailLocked = false
    @FocusState private var focus: Field?

    private enum Field: Hashable { case first, last, email }

    private var emailOK: Bool {
        email.trimmingCharacters(in: .whitespaces).range(of: #"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$"#, options: .regularExpression) != nil
    }
    private var valid: Bool { !first.trimmed.isEmpty && !last.trimmed.isEmpty && emailOK }

    var body: some View {
        VStack(spacing: 0) {
            if !firstTime { ScreenTopBar(title: L("details_title"), onBack: onDone) }
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if firstTime {
                        Text(L("details_welcome_title")).font(PKFont.headlineSmall).foregroundStyle(Color.pkText).padding(.top, 32)
                        Text(L("details_welcome_body")).font(PKFont.bodyMedium).foregroundStyle(Color.pkMuted)
                        Text(L("details_pick_avatar")).font(PKFont.labelLarge).foregroundStyle(Color.pkText).padding(.top, 4)
                        AvatarGrid(selected: avatar) { avatar = $0 }.padding(.bottom, 8)
                    }
                    LabeledField(label: L("details_first_name"), text: $first, capitalization: .words, content: .givenName, identifier: "details_first") { focus = .last }
                        .focused($focus, equals: .first).id(Field.first)
                    LabeledField(label: L("details_last_name"), text: $last, capitalization: .words, content: .familyName, identifier: "details_last") { focus = emailLocked ? nil : .email }
                        .focused($focus, equals: .last).id(Field.last)
                    LabeledField(label: L("details_email"), text: $email, keyboard: .emailAddress, capitalization: .never, disabled: emailLocked, isError: !email.isEmpty && !emailOK, content: .emailAddress, identifier: "details_email") { focus = nil }
                        .focused($focus, equals: .email).id(Field.email)
                    if !email.isEmpty && !emailOK {
                        Text(L("details_email_invalid")).font(PKFont.bodySmall).foregroundStyle(Color.pkError)
                    }
                    Text(L("details_privacy")).font(PKFont.bodySmall).foregroundStyle(Color.pkMuted)
                    if let error { Text(error).font(PKFont.bodyMedium).foregroundStyle(Color.pkError) }
                }
                .padding(.horizontal, 20).padding(.bottom, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .clipped() // nothing scrolls under the status bar
            .onChange(of: focus) { _, field in
                // The avatars above push the fields under the keyboard otherwise.
                if let field { withAnimation { proxy.scrollTo(field, anchor: .center) } }
            }
            }
            Button(action: save) {
                if saving { ProgressView().tint(Color.pkOnPrimary) } else { Text(L(firstTime ? "driver_save_and_continue" : "driver_save")) }
            }
            .buttonStyle(PillButtonStyle())
            .disabled(!valid || saving)
            .padding(.horizontal, 16).padding(.vertical, 12)
        }
        .background(Color.pkBackground.ignoresSafeArea())
        .task {
            // Prefill whatever is already known, such as the address just proved by email.
            if let account = (try? await API.shared.myProfile())?.account {
                first = account.firstName ?? ""
                last = account.lastName ?? ""
                email = account.email ?? ""
                emailLocked = account.emailVerified == true
            }
        }
    }

    private func save() {
        saving = true
        error = nil
        Task {
            defer { saving = false }
            do {
                _ = try await API.shared.updateDetails(DetailsUpdate(firstName: first.trimmed, lastName: last.trimmed, email: email.trimmed))
                if firstTime, let avatar { try? await API.shared.setAvatar(avatar) }
                if let token = session.token, let id = session.userId, let initial = last.trimmed.first {
                    session.save(token: token, userId: id, displayName: "\(first.trimmed) \(initial).")
                }
                onDone()
            } catch {
                self.error = friendly(error)
            }
        }
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
