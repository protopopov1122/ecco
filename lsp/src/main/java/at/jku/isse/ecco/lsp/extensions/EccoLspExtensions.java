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
}
