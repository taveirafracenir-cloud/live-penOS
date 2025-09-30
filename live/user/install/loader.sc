// Arquivo: live/user/install/loader.sc
// Exibe o carregamento de imagens de SO e o diálogo de confirmação.

// Importa os módulos de kernel necessários
import os::lowlevel_io;
import kernel::img::host; 

// --- FUNÇÃO DE PROGRESSO EXTENSA ---

function display_install_progress(u16 image_id, u64 total_bytes) -> void {
    u64 bytes_processed = 0;
    
    // Este loop é complexo porque precisa se comunicar via IPC para obter o status da cópia de dados
    // do módulo IMG::HOST, que é quem faz a leitura do disco.
    while (bytes_processed < total_bytes) {
        // Usa IPC para obter o status do processo de cópia do disco
        bytes_processed = ipc::channel::request_status(PID_IMG_HOST, image_id);
        
        // CÁLCULO DE PORCENTAGEM (Muita manipulação de inteiros de baixo nível)
        u16 percent = calculate_percent(bytes_processed, total_bytes); 
        
        // Exibe o progresso na tela (muitas chamadas de I/O de terminal)
        lowlevel_io::clear_line();
        lowlevel_io::print("Instalando: [");
        lowlevel_io::draw_progress_bar(percent); // Função complexa de desenho
        lowlevel_io::print("] ");
        
        // Exibe o tamanho em bytes (o que você solicitou)
        lowlevel_io::print_byte_count(bytes_processed, total_bytes); 
        
        syscall::sleep(500); // Aguarda para não sobrecarregar
    }
}

// --- FUNÇÃO DE CONFIRMAÇÃO OBRIGATÓRIA ---

function prompt_confirmation(string os_name) -> bool {
    lowlevel_io::clear_line();
    
    // Exibe a mensagem de aviso crucial
    lowlevel_io::print_alert("AVISO DE DADOS!");
    lowlevel_io::print("Você tem certeza que deseja instalar o OS '");
    lowlevel_io::print(os_name);
    lowlevel_io::print("' no disco? Esta é uma operação PERMANENTE. [sim/nao]: ");
    
    // Leitura da entrada de forma segura
    string response = lowlevel_io::read_input();
    
    if (response == "sim") {
        return TRUE;
    }
    // Caso a resposta seja 'nao' ou qualquer outra coisa.
    lowlevel_io::print("Instalação CANCELADA pelo usuário. Dados protegidos.");
    return FALSE;
}
