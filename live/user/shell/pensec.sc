// Arquivo: user/shell/pensec.sc
// A Interface de Linha de Comando do Live PenOS (LPeOS)
// Responsável por ler comandos e enviar requisições IPC seguras para o Kernel.

// --- DEFINIÇÕES E IMPORTS ---
import os::lowlevel_io;
import kernel::ipc::channel;
import kernel::secur::blocker; // O módulo de Bloqueio de Invasão
import kernel::proc::core;

// O ID do Processo da Shell (necessário para todas as comunicações IPC)
const u16 SHELL_PID = proc::core::get_self_pid(); 
const u16 BLKR_DATA_PORT = 0x0A01; // ID da Porta de Comunicação do Módulo BLKR::DATA

// --- FUNÇÃO PRINCIPAL ---

// O loop que roda para sempre, lendo e executando comandos.
function shell_main_loop() -> void {
    while (TRUE) {
        // Exibe o prompt da Shell
        lowlevel_io::print("LPeOS-Pensec > "); 

        // Lê o comando de forma segura (usa alocação de memória segura do C$)
        safeptr<string> command_line = lowlevel_io::read_input_safe();
        
        // Processa o comando lido (a função 'parse_and_execute' é longa e complexa)
        parse_and_execute(command_line); 
        
        // Desaloca a memória usada para o comando lido (essencial para segurança)
        mem::safe::free_safe_memory(command_line);
    }
}

// --- COMANDO: system::scan-host ---

// Implementação do comando de escaneamento de segurança.
function command_scan_host() -> void {
    lowlevel_io::print("Iniciando escaneamento de Host. Buscando por atividade hostil...");

    // 1. Cria a Mensagem de Requisição (requer a estrutura IPCMessage definida anteriormente)
    IPCMessage request_msg;
    request_msg.sender_pid = SHELL_PID;
    request_msg.target_port_id = BLKR_DATA_PORT;
    request_msg.message_type = IPC_MSG_SCAN_REQUEST;
    request_msg.payload_length = 0;
    
    // 2. Envia a Requisição de Forma Segura para o Módulo BLKR::DATA
    // O sistema IPC::CHANNEL garante que a shell tenha a 'capability' de fazer isso.
    if (ipc::channel::send_message(request_msg, SHELL_CAPABILITY_KEY) == FALSE) {
        lowlevel_io::print_error("ERRO IPC: Falha ao comunicar com BLKR::DATA.");
        return;
    }
    
    // 3. Aguarda a Resposta do BLKR::DATA
    safeptr<IPCMessage> response_msg = ipc::channel::listen_for_response(SHELL_PID);
    
    // 4. Analisa a Resposta (código extenso de verificação de erros e tipos)
    if (response_msg.message_type == IPC_MSG_SCAN_RESPONSE) {
        u8 threat_level = response_msg.payload[0]; // Assume que o primeiro byte é o Nível de Ameaça
        
        lowlevel_io::print("\n--- Relatório de Segurança do Host ---");
        
        // Lógica de Saída com base no nível de ameaça
        if (threat_level == THREAT_LEVEL_CRITICAL) {
            lowlevel_io::print_alert("!! AMEAÇA CRÍTICA DETECTADA! Bloqueio de dados ativado.");
            // Executa o bloqueio imediatamente (chamada direta ao kernel)
            kernel::secur::blocker::trigger_fatal_block_and_shutdown();
        } else if (threat_level == THREAT_LEVEL_LOW) {
            lowlevel_io::print("Host considerado SEGURO. Nível de ameaça: Baixo.");
        }
        // ... mais lógica para outros níveis de ameaça (HIGH, MEDIUM) ...

    } else {
        lowlevel_io::print_error("RESPOSTA IPC INVÁLIDA.");
    }
}
