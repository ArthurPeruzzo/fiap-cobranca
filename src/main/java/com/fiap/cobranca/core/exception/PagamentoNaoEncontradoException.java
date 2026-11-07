package com.fiap.cobranca.core.exception;

import com.fiap.cobranca.shared.exception.BaseException;

public class PagamentoNaoEncontradoException extends BaseException {

    public PagamentoNaoEncontradoException(Long ordemServicoId) {
        super(404, "Pagamento da ordem de serviço " + ordemServicoId + " não encontrado");
    }
}
