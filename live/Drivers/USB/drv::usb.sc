// Arquivo: kernel/drv/usb/core.sc
// Módulo do Driver USB (DRV::USB)
// Complexidade: Gerenciamento de Interrupções de Hardware e Agendamento de Pacotes.

// --- ESTRUTURA DE DADOS PRINCIPAL ---

// 1. Estrutura que representa um Dispositivo USB Conectado
struct UsbDevice {
    u8 device_address;          // Endereço único do dispositivo na porta USB
    u16 vendor_id;              // ID do Fabricante
    u16 product_id;             // ID do Produto

    // Flag de status: TRUE se o dispositivo estiver sendo usado por algum processo LPeOS
    bool is_in_use_lock;

    // Lista de "EndPoints" (os canais de comunicação do dispositivo - muito complexo)
    safeptr<UsbEndpoint> endpoint_list_head; 
    
    // Ponteiro seguro para o descritor de memória usado pelo hardware (DMA Buffer)
    safeptr<byte> dma_buffer_safe_ptr; 
    
    // Ponteiro para o próximo dispositivo na lista do hub
    safeptr<UsbDevice> next_device; 
}

// 2. Estrutura de um "Pacote de Transferência" USB
// Cada requisição de I/O é dividida em muitos pacotes como este.
struct UsbTransferPacket {
    u16 packet_id;              // ID da Transação
    u8 target_endpoint;          // Qual canal de comunicação do dispositivo usar
    
    // O ponteiro seguro para o buffer de dados a ser enviado/recebido
    safeptr<byte> data_buffer; 
    u32 data_length;
    
    // Status do Pacote (PENDING, COMPLETE, ERROR)
    u8 status_code;

    // Função de Retorno de Chamada: Qual processo LPeOS deve ser notificado ao terminar.
    u64 callback_function_ptr; 
    
    // Lista ligada: Próximo pacote na fila de agendamento (o que o torna complexo)
    safeptr<UsbTransferPacket> next_in_queue; 
}


// --- FUNÇÕES COMPLEXAS DO KERNEL EM C$ ---

// O Agendador de Pacotes USB (Onde o código é muito longo e difícil)
// Responsável por organizar a ordem de envio/recebimento de todos os pacotes de dados.
function usb_scheduler_loop() -> void {
    while (TRUE) {
        // 1. Verifica se há interrupções de hardware (pacotes recebidos).
        if (syscall::check_hardware_interrupt("USB_CTRL") == TRUE) {
            handle_hardware_interrupt();
        }

        // 2. Seleciona o próximo Pacote Pendente de Maior Prioridade.
        safeptr<UsbTransferPacket> next_packet = select_high_priority_packet();
        
        // 3. Monta o pacote no formato de hardware (requer muitas linhas de manipulação de bits).
        u64 hardware_register_data = translate_to_hardware_format(next_packet); 

        // 4. Envia para o controlador USB (muitas chamadas de baixo nível).
        syscall::write_hardware_register(USB_HC_QUEUE_REGISTER, hardware_register_data); 
        
        // 5. Atualiza o status do Pacote e move para a fila de espera.
        next_packet.status_code = STATUS_PENDING;

        // ... O código aqui lida com o complexo processo de agendamento em milissegundos ...
    }
}

// Lida com a Interrupção: Quando o hardware diz "Recebi/Enviei um pacote!"
function handle_hardware_interrupt() -> void {
    // 1. Lê os Registros de Status do Controlador para saber qual evento ocorreu.
    u32 status_reg = syscall::read_hardware_register(USB_HC_STATUS_REGISTER);
    
    // 2. Itera sobre a fila de espera para encontrar qual pacote foi concluído.
    safeptr<UsbTransferPacket> completed_packet = find_completed_packet(status_reg);
    
    // 3. Se um pacote foi concluído: Chama a função de Retorno de Chamada (Callback)
    // Isso usa o módulo IPC::CHANNEL para notificar o processo que solicitou a transferência.
    ipc::channel::notify_process_callback(completed_packet.callback_function_ptr, completed_packet.data_buffer);
    
    // 4. Libera o Pacote da Fila e a Memória (usando mem::safe::free_safe_memory).
}
