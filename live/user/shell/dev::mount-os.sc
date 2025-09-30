// Arquivo: user/shell/pensec.sc (continuação)
// Implementação do comando dev::mount-os.

// Módulo de Confirmação (loader.sc)
import user::install::loader;

// Módulo do Kernel (IMG::HOST)
import kernel::img::host;

// Comando para iniciar o processo de 'Fábrica de SOs'
function command_mount_os() -> void {
    lowlevel_io::print("--- MODO FÁBRICA DE SOs ---");
    lowlevel_io::print("Digite o caminho do arquivo de imagem (.iso, .img) do SO:");
    
    // 1. Leitura Segura do Caminho (usa safeptr)
    safeptr<string> image_path = lowlevel_io::read_input_safe();
    
    if (image_path.length == 0) {
        lowlevel_io::print_error("Caminho não pode ser vazio.");
        mem::safe::free_safe_memory(image_path);
        return;
    }

    lowlevel_io::print("Montando imagem: ");
    lowlevel_io::print(image_path);

    // 2. Monta a Imagem Virtualmente no Kernel (Chama IMG::HOST)
    // Esta é uma chamada de Kernel (syscall) que é extensa e demorada.
    u16 image_id = syscall::mount_virtual_image(image_path); 

    if (image_id == 0) {
        lowlevel_io::print_error("Falha ao montar a imagem. Verifique o arquivo.");
        mem::safe::free_safe_memory(image_path);
        return;
    }

    // 3. Obtém o tamanho do arquivo (para exibir o progresso em bytes)
    u64 total_bytes = syscall::get_file_size(image_path);

    // 4. Exibe o aviso e pede Confirmação Final (Chama loader.sc)
    // O sistema só avança se o usuário digitar 'sim'.
    if (loader::prompt_confirmation(image_path) == FALSE) {
        // Se cancelado, desmonta a imagem e sai.
        syscall::unmount_virtual_image(image_id);
        mem::safe::free_safe_memory(image_path);
        return;
    }

    // 5. Inicia o Carregamento Visual (com % e Bytes)
    // Este loop de progresso interage continuamente com o Kernel via IPC.
    loader::display_install_progress(image_id, total_bytes);

    // 6. Inicia a Rotina Final de Instalação (Chama kernel/boot/exec.sc)
    // Esta função é o ponto de não retorno; ela grava os dados no disco final.
    if (syscall::execute_final_boot_routine(image_id) == TRUE) {
        lowlevel_io::print("\n\n!! INSTALAÇÃO DO NOVO SO CONCLUÍDA !!");
        lowlevel_io::print("O Live PenOS será desligado. Retire o pendrive para iniciar o novo SO.");
        
        // Desliga o LPeOS para que o computador possa inicializar a nova instalação
        syscall::power_off(POWER_OFF_MODE_SAFE); 
    } else {
        lowlevel_io::print_error("\nERRO CRÍTICO: Falha na Rotina Final de Boot. Dados podem estar incompletos.");
    }
    
    mem::safe::free_safe_memory(image_path);
}
