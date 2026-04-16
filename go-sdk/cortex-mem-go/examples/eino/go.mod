module github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/examples/eino

go 1.22

require (
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go v0.0.0
)

replace (
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go => ../..
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/eino => ../../eino
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/genkit => ../../genkit
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/langchaingo => ../../langchaingo
)
