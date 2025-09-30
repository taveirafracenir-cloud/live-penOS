function security_monitor_loop() -> void {
    while (current_security_state != STATE_LOCKED_FATAL) {
        // Recebe um evento de I/O do Driver USB (usando IPC)
        safeptr<UsbTransferPacket> incoming_event = ipc::channel::listen_for_io_event(DRV_USB_PORT);

        if (incoming_event != NULL) {
            // 1. ANÁLISE PROFUNDA DO PACOTE (A parte longa e difícil)
            // Esta função tem mais de 80 linhas, decodificando o comando de I/O USB
            // para ver o que o host está tentando fazer.
            u16 behavior = analyze_io_command(incoming_event); 
            
            if (behavior == BEHAVIOR_TRUSTED) {
                // Ação normal (ex: host lendo o nome de volume)
                update_trust_signature(incoming_event);
            } else if (behavior == BEHAVIOR_SUSPICIOUS) {
                // Incrementa o contador do comportamento hostil (Ex: 3 falhas de leitura).
                if (increment_hostile_counter(behavior) >= 5) {
                    trigger_fatal_block_and_shutdown();
                }
            }
        }
        // Espera um ciclo curto para não consumir CPU
        syscall::sleep(10); 
    }
}

// A função de Resposta e Bloqueio Final
function trigger_fatal_block_and_shutdown() -> void {
    current_security_state = STATE_LOCKED_FATAL;
    
    // 1. Ação de Bloqueio Rápido: Diz ao Driver USB para parar de responder.
    drv::usb::set_usb_port_status(USB_PORT_STATUS_IGNORE);

    // 2. Limpeza de Memória: Limpa todos os dados sensíveis da RAM (usa o MEM::SAFE).
    // O código aqui é extenso, liberando blocos de memória em tempo de crise.
    mem::safe::wipe_all_sensitive_data(); 

    // 3. Opcional (Se configurado): Emite um sinal de desligamento imediato.
    // Isso garante que o pendrive seja ejetado de forma segura e imediata.
    syscall::power_off(POWER_OFF_MODE_ABRUPT); 
}
