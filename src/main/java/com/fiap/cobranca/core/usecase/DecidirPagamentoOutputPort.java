package com.fiap.cobranca.core.usecase;

import com.fiap.cobranca.core.domain.Pagamento;

public interface DecidirPagamentoOutputPort {

    void apresentar(Pagamento pagamento);
}
