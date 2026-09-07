import SwiftUI
import SafariServices
import WebKit

#if os(iOS)
import UIKit
typealias PlatformViewController = UIViewController
#elseif os(macOS)
import AppKit
typealias PlatformViewController = NSViewController
#endif

#if os(macOS)
private enum SafariActivation {
    private static let extensionBundleIdentifier = "com.xiaoshuo.yijianhuanming.Extension"

    static func open() {
        SFSafariApplication.showPreferencesForExtension(
            withIdentifier: extensionBundleIdentifier
        ) { _ in }
    }
}
#endif

@available(iOS 15.0, macOS 12.0, *)
private struct ActivationStep: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(String(number))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(Color.accentColor, in: Circle())

            Text(text)
                .font(.body)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

@available(iOS 15.0, macOS 12.0, *)
private struct HostAppView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image("LargeIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .accessibilityHidden(true)

                VStack(spacing: 8) {
                    Text("小说一键换名")
                        .font(.title2.weight(.semibold))

                    Text("在 Safari 看小说时，把角色名换成你想看的名字。")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 16) {
                    ActivationStep(number: 1, text: "在 Safari 设置中启用“小说一键换名”")
                    ActivationStep(number: 2, text: "允许扩展访问正在阅读的网站")
                    ActivationStep(number: 3, text: "回到小说页面，点扩展入口设置名字")
                }
                .padding()
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 16))

#if os(iOS)
                VStack(spacing: 6) {
                    Text("请打开：设置 > Apps > Safari > 扩展")
                        .font(.headline)
                    Text("Apple 暂不提供直接跳转到 Safari 扩展设置的公开接口。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
#elseif os(macOS)
                Button("去启用 Safari 扩展") {
                    SafariActivation.open()
                }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
#endif

                Text("启用一次后，日常使用都在 Safari 阅读页面中完成。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Divider()

                Label(
                    "规则保存在本机，小说正文不会被主动上传。",
                    systemImage: "hand.raised"
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
    }
}

final class ViewController: PlatformViewController {
    @IBOutlet private var webView: WKWebView!

    override func viewDidLoad() {
        super.viewDidLoad()

#if os(iOS)
        webView.removeFromSuperview()
        let host = UIHostingController(rootView: HostAppView())
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        host.didMove(toParent: self)
#elseif os(macOS)
        guard #available(macOS 12.0, *) else { return }
        webView.removeFromSuperview()
        let host = NSHostingController(rootView: HostAppView())
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
#endif
    }
}
