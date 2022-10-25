package at.jku.isse.ecco.lsp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;

import java.nio.file.Paths;

public class EccoLsp implements EccoListener {

	public static void main(String[] args) {
		EccoLsp lsp = new EccoLsp();
	}

	EccoLsp() {
		// ECCO Service
		this.eccoService = new EccoService(Paths.get("").toAbsolutePath()); // create ecco service
		eccoService.detectRepository(Paths.get("").toAbsolutePath()); // detect any existing repository
	}

	private EccoService eccoService;


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			System.out.println("ECCO - " + this.eccoService.getRepositoryDir());
		} else {
			System.out.println("ECCO");
		}
	}

}
