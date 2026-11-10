package com.fiap.cobranca.core.exception;

import com.fiap.cobranca.core.domain.StatusPagamento;
import com.fiap.cobranca.shared.exception.BaseException;

public class PagamentoJaDecididoException extends BaseException {

    public PagamentoJaDecididoException(Long ordemServicoId, StatusPagamento atual) {
        super(409, "Pagamento da ordem de serviço " + ordemServicoId + " já está " + atual);
    }
}
