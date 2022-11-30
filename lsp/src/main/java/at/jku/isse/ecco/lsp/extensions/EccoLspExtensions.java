package at.jku.isse.ecco.lsp.extensions;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

import java.util.concurrent.CompletableFuture;

@JsonSegment("ecco")
public interface EccoLspExtensions {

    @JsonRequest("checkout")
    CompletableFuture<CheckoutResponse> checkout(CheckoutRequest request);

    @JsonRequest("commit")
    CompletableFuture<CommitResponse> commit(CommitRequest request);

    @JsonRequest("info")
    CompletableFuture<InfoResponse> info(InfoRequest request);

    @JsonRequest("documentAssociations")
    CompletableFuture<DocumentAssociationsResponse> getDocumentAssociations(DocumentAssociationsRequest request);

    @JsonRequest("documentFeatures")
    CompletableFuture<DocumentFeaturesResponse> getDocumentFeatures(DocumentFeaturesRequest request);

    @JsonRequest("getSettings")
    CompletableFuture<SettingsState> getSettings(SettingsRequest request);

    @JsonRequest("updateSettings")
    CompletableFuture<SettingsState> updateSettings(SettingsState request);
}
