package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;

public interface ConsultarPagamentoOutputPort {

    void apresentar(Pagamento pagamento);
}
